package com.pushpendersingh.reactnativescanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CameraManager(private val reactContext: ReactApplicationContext) {

    private val TAG = "CameraManager"
    @Volatile
    private var cameraProvider: ProcessCameraProvider? = null
    @Volatile
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var scanner: BarcodeScanner? = null
    @Volatile
    private var cameraControl: CameraControl? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var previewView: PreviewView? = null
    
    private val isScanning = AtomicBoolean(false)
    private val scanCallbackRef = AtomicReference<((WritableArray) -> Unit)?>(null)
    
    private val cameraBindLock = ReentrantLock()
    @Volatile
    private var isBinding = false
    private val executorLock = ReentrantLock()

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val MAX_BITMAP_PIXELS = 4_000_000 // ~4MP (2000×2000) for first attempt
        private const val FALLBACK_MAX_PIXELS = 8_000_000 // ~8MP for retry with higher resolution
        private const val MAX_CLEANUP_RETRIES = 10 // Max retries for performFullCleanup
        private val threadCount = AtomicInteger(0)
    }
    
    private val executorThreadFactory = ThreadFactory { r ->
        Thread(r, "CameraManager-${threadCount.incrementAndGet()}").apply {
            isDaemon = true
        }
    }

    fun hasCameraPermission(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(reactContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun bindPreviewView(view: PreviewView) {
        this.previewView = view
        // Only rebind if scanning AND not already in binding process
        if (isScanning.get() && !isBinding) {
            // Schedule rebind on main executor to avoid race with initializeCamera callback
            ContextCompat.getMainExecutor(reactContext).execute {
                bindCameraUseCases()
            }
        }
    }

    /**
     * Thread-safe executor lifecycle management
     * Ensures the camera executor is available and not shutdown.
     * Creates a new executor if the current one has been shutdown.
     * Uses dedicated lock to prevent race with releaseCamera()
     */
    private fun ensureExecutor() {
        executorLock.withLock {
            if (cameraExecutor.isShutdown) {
                cameraExecutor = Executors.newSingleThreadExecutor(executorThreadFactory)
                Log.d(TAG, "♻️ Recreated camera executor")
            }
        }
    }
    
    /**
     * Safe executor access with validation
     * Returns the executor only if it's not shutdown
     */
    private fun getExecutorSafely(): ExecutorService? {
        return executorLock.withLock {
            if (!cameraExecutor.isShutdown) cameraExecutor else null
        }
    }

    /**
     * Lock-free scanning with atomic CAS operation
     * Eliminates race condition between check and set
     */
    fun startScanning(callback: (WritableArray) -> Unit) {
        if (!hasCameraPermission()) {
            throw SecurityException("Camera permission not granted")
        }

        // Atomic compare-and-set: only first caller proceeds
        if (!isScanning.compareAndSet(false, true)) {
            Log.w(TAG, "Scanning already in progress, updating callback")
            scanCallbackRef.set(callback)
            return
        }

        // At this point, we're guaranteed to be the only thread starting scanning
        // Set callback immediately after winning the CAS race
        scanCallbackRef.set(callback)
        
        try {
            ensureExecutor()
            initializeCamera()
        } catch (e: Exception) {
            // Reset flag on error
            isScanning.set(false)
            scanCallbackRef.set(null)
            throw e
        }
    }

    private fun initializeCamera() {
        Log.d(TAG, "Initializing camera...")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(reactContext)

        // Initialize barcode scanner if not already created
        if (scanner == null) {
            Log.d(TAG, "Creating barcode scanner...")
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_AZTEC,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_CODE_93,
                    Barcode.FORMAT_CODABAR,
                    Barcode.FORMAT_DATA_MATRIX,
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_ITF,
                    Barcode.FORMAT_PDF417,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E
                )
                .build()
            
            scanner = BarcodeScanning.getClient(options)
        }

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (exc: Exception) {
                Log.e(TAG, "Error initializing camera: ${exc.message}", exc)
                // Reset state on error
                isScanning.set(false)
                scanCallbackRef.set(null)
            }
        }, ContextCompat.getMainExecutor(reactContext))
    }

    /**
     * Thread-safe camera binding with lock
     * Prevents concurrent binding operations that could cause IllegalStateException
     */
    private fun bindCameraUseCases() {
        // Use lock to serialize all binding operations
        cameraBindLock.withLock {
            // Check and set binding flag atomically within lock
            if (isBinding) {
                Log.w(TAG, "⚠️ Camera binding already in progress, skipping")
                return
            }
            isBinding = true
        }
        
        try {
            Log.d(TAG, "Binding camera use cases...")
            val currentActivity = reactContext.currentActivity as? AppCompatActivity
            if (currentActivity == null || currentActivity.isDestroyed || currentActivity.isFinishing) {
                Log.e(TAG, "❌ Current activity is not available")
                isScanning.set(false)
                scanCallbackRef.set(null)
                return
            }

            // Get executor safely - may be null if shutdown in progress
            val executor = getExecutorSafely()
            if (executor == null) {
                Log.e(TAG, "❌ Executor is shutdown, cannot bind camera")
                isScanning.set(false)
                scanCallbackRef.set(null)
                return
            }

            // Unbind any existing use cases first
            cameraProvider?.unbindAll()
            Log.d(TAG, "Unbound previous camera use cases")

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Setup preview
            preview = Preview.Builder()
                .build()
                .also {
                    previewView?.let { view ->
                        it.setSurfaceProvider(view.surfaceProvider)
                    }
                }

            // Use the safely-obtained executor reference
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(executor) { imageProxy ->
                        // Check atomic boolean
                        if (!isScanning.get()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            processImage(imageProxy, mediaImage)
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            // Bind use cases to camera - include preview if available
            val useCases = mutableListOf<androidx.camera.core.UseCase>(imageAnalysis!!)
            preview?.let { useCases.add(it) }

            val camera = cameraProvider?.bindToLifecycle(
                currentActivity,
                cameraSelector,
                *useCases.toTypedArray()
            )
            
            cameraControl = camera?.cameraControl
            Log.d(TAG, "✅ Camera successfully bound and scanning started (preview: ${preview != null})")
        } catch (exc: Exception) {
            Log.e(TAG, "Error binding camera use cases: ${exc.message}", exc)
            // Reset state on binding error
            isScanning.set(false)
            scanCallbackRef.set(null)
        } finally {
            // Always reset binding flag in finally block
            cameraBindLock.withLock {
                isBinding = false
            }
        }
    }

    private fun processImage(imageProxy: androidx.camera.core.ImageProxy, mediaImage: android.media.Image) {
        val scanner = this.scanner
        if (scanner == null) {
            imageProxy.close()
            return
        }

        val image = com.google.mlkit.vision.common.InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    val results = Arguments.createArray()
                    var hasValidBarcode = false
                    
                    for (barcode in barcodes) {
                        if (!barcode.rawValue.isNullOrEmpty()) {
                            val result = createBarcodeResult(barcode)
                            results.pushMap(result)
                            hasValidBarcode = true
                        }
                    }
                    
                    if (hasValidBarcode) {
                        val callback = scanCallbackRef.get()
                        callback?.invoke(results)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Barcode scanning failed: ${e.message}", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun createBarcodeResult(barcode: Barcode): WritableMap {
        val result = Arguments.createMap()
        result.putString("data", barcode.rawValue ?: "")
        result.putString("type", getBarcodeTypeName(barcode.format))

        barcode.boundingBox?.let { bounds ->
            val boundsMap = Arguments.createMap()
            boundsMap.putDouble("width", bounds.width().toDouble())
            boundsMap.putDouble("height", bounds.height().toDouble())

            val origin = Arguments.createMap()
            origin.putMap("topLeft", createPoint(bounds.left.toDouble(), bounds.top.toDouble()))
            origin.putMap("bottomLeft", createPoint(bounds.left.toDouble(), bounds.bottom.toDouble()))
            origin.putMap("bottomRight", createPoint(bounds.right.toDouble(), bounds.bottom.toDouble()))
            origin.putMap("topRight", createPoint(bounds.right.toDouble(), bounds.top.toDouble()))

            boundsMap.putMap("origin", origin)
            result.putMap("bounds", boundsMap)
        }

        return result
    }

    private fun createPoint(x: Double, y: Double): WritableMap {
        val point = Arguments.createMap()
        point.putDouble("x", x)
        point.putDouble("y", y)
        return point
    }

    private fun getBarcodeTypeName(format: Int): String {
        return when (format) {
            Barcode.FORMAT_QR_CODE -> "QR_CODE"
            Barcode.FORMAT_AZTEC -> "AZTEC"
            Barcode.FORMAT_CODE_128 -> "CODE_128"
            Barcode.FORMAT_CODE_39 -> "CODE_39"
            Barcode.FORMAT_CODE_93 -> "CODE_93"
            Barcode.FORMAT_CODABAR -> "CODABAR"
            Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
            Barcode.FORMAT_EAN_13 -> "EAN_13"
            Barcode.FORMAT_EAN_8 -> "EAN_8"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_PDF417 -> "PDF417"
            Barcode.FORMAT_UPC_A -> "UPC_A"
            Barcode.FORMAT_UPC_E -> "UPC_E"
            else -> "UNKNOWN"
        }
    }

    /**
     * Uses atomic operation to stop scanning
     * Thread-safe: Can be called from any thread, camera operations executed on main thread
     */
    fun stopScanning() {
        // Atomic CAS: only proceed if actually scanning
        if (!isScanning.compareAndSet(true, false)) {
            Log.w(TAG, "Scanning is not in progress")
            return
        }

        try {
            Log.d(TAG, "Stopping scanning...")
            scanCallbackRef.set(null)
            
            // Execute camera operations on main thread to avoid IllegalStateException
            ContextCompat.getMainExecutor(reactContext).execute {
                try {
                    // Clear the analyzer to stop processing frames
                    imageAnalysis?.clearAnalyzer()
                    
                    // Unbind all use cases from the camera
                    cameraProvider?.unbindAll()
                    
                    // Clear references but DON'T shutdown executor or scanner
                    // They will be reused if scanning starts again
                    cameraControl = null
                    imageAnalysis = null
                    preview = null
                    
                    Log.d(TAG, "✅ Scanning stopped successfully (executor kept alive for reuse)")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping scanning on main thread: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scanning: ${e.message}", e)
            // Ensure flag is still set to false even on error
            isScanning.set(false)
            throw e
        }
    }

    fun enableFlashlight() {
        try {
            cameraControl?.enableTorch(true)
            Log.d(TAG, "Flashlight enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling flashlight: ${e.message}", e)
            throw e
        }
    }

    fun disableFlashlight() {
        try {
            cameraControl?.enableTorch(false)
            Log.d(TAG, "Flashlight disabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling flashlight: ${e.message}", e)
            throw e
        }
    }

    /**
     * All file operations are performed on background thread.
     * Callback is always dispatched to main thread for React Native compatibility.
     */
    fun scanImage(imageUri: String, callback: (WritableArray) -> Unit) {
        // Validate imageUri before processing
        if (imageUri.isBlank()) {
            Log.w(TAG, "scanImage called with blank imageUri")
            ContextCompat.getMainExecutor(reactContext).execute {
                callback(Arguments.createArray())
            }
            return
        }
        
        // Ensure executor is available
        ensureExecutor()
        
        // Execute all file I/O on background thread
        // Wrap in try-catch to handle race condition where executor could be shutdown
        // between ensureExecutor() and execute()
        try {
            cameraExecutor.execute {
                try {
                    val uri = if (imageUri.startsWith("file://")) {
                        val path = imageUri.replace("file://", "")
                        Uri.fromFile(java.io.File(path))
                    } else {
                        Uri.parse(imageUri)
                    }

                    Log.d(TAG, "Scanning image from URI: $uri")

                    // Try InputImage.fromFilePath
                    // It handles Exif and memory efficiently
                    try {
                        val image = com.google.mlkit.vision.common.InputImage.fromFilePath(reactContext, uri)
                        processImageOnBackground(image, null, uri, false, callback)
                    } catch (e: java.io.IOException) {
                        Log.w(TAG, "InputImage.fromFilePath failed (${e.message}), falling back to Bitmap loader")
                        // Fallback to manual Bitmap loading with size limits
                        scanImageWithRetry(uri, callback, isRetry = false)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Scan image failed", e)
                    // Always callback on main thread, even on error
                    ContextCompat.getMainExecutor(reactContext).execute {
                        callback(Arguments.createArray())
                    }
                }
            }
        } catch (e: RejectedExecutionException) {
            Log.w(TAG, "Executor was shutdown, cannot scan image: ${e.message}")
            ContextCompat.getMainExecutor(reactContext).execute {
                callback(Arguments.createArray())
            }
        }
    }
    
    /**
     * Retry mechanism with higher resolution if first scan fails
     * First attempt uses MAX_BITMAP_PIXELS (4MP), retry uses FALLBACK_MAX_PIXELS (8MP)
     */
    private fun scanImageWithRetry(
        uri: Uri,
        callback: (WritableArray) -> Unit,
        isRetry: Boolean = false
    ) {
        val maxPixels = if (isRetry) FALLBACK_MAX_PIXELS else MAX_BITMAP_PIXELS
        val bitmap = loadBitmap(uri, maxPixels)
        
        if (bitmap != null) {
            Log.d(TAG, "Bitmap loaded: ${bitmap.width}x${bitmap.height}, isRetry=$isRetry")
            val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            processImageOnBackground(image, bitmap, uri, isRetry, callback)
        } else {
            Log.e(TAG, "Failed to load bitmap from URI")
            // Always callback on main thread
            ContextCompat.getMainExecutor(reactContext).execute {
                callback(Arguments.createArray())
            }
        }
    }

    /**
     * Process image for barcode scanning (for static images).
     * Includes retry logic if no barcodes found on first attempt.
     * Always calls callback, even on error.
     * Callback is always dispatched to main thread.
     */
    private fun processImageOnBackground(
        image: com.google.mlkit.vision.common.InputImage,
        bitmapToRecycle: Bitmap?,
        uri: Uri,
        isRetry: Boolean,
        callback: (WritableArray) -> Unit
    ) {
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
        )

        // Flag to track if we're retrying (to avoid double cleanup in onCompleteListener)
        var didRetry = false
        
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val results = Arguments.createArray()
                for (barcode in barcodes) {
                    if (!barcode.rawValue.isNullOrEmpty()) {
                        results.pushMap(createBarcodeResult(barcode))
                    }
                }
                Log.d(TAG, "Scan complete. Found ${barcodes.size} barcodes.")
                
                // Retry with higher resolution if no results and not already retrying
                if (results.size() == 0 && !isRetry && bitmapToRecycle != null) {
                    Log.d(TAG, "No barcodes found, retrying with higher resolution")
                    didRetry = true
                    // Recycle current bitmap before retry
                    if (!bitmapToRecycle.isRecycled) {
                        bitmapToRecycle.recycle()
                    }
                    scanner.close()
                    scanImageWithRetry(uri, callback, isRetry = true)
                } else {
                    // Dispatch callback to main thread
                    ContextCompat.getMainExecutor(reactContext).execute {
                        callback(results)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Barcode scanning failed: ${e.message}", e)
                // Always callback on main thread
                ContextCompat.getMainExecutor(reactContext).execute {
                    callback(Arguments.createArray())
                }
            }
            .addOnCompleteListener {
                // Only cleanup if we didn't retry (retry handles its own cleanup)
                if (!didRetry) {
                    scanner.close()
                    // Recycle bitmap after processing to free memory
                    bitmapToRecycle?.let { bitmap ->
                        if (!bitmap.isRecycled) {
                            bitmap.recycle()
                            Log.d(TAG, "Recycled bitmap after image processing")
                        }
                    }
                }
            }
    }

    /**
     * Load bitmap with size limits to prevent OOM.
     * Uses two-pass decoding: first decode bounds only, then decode with sample size.
     * @param uri The URI of the image to load
     * @param maxPixels Maximum pixels allowed (width * height)
     */
    private fun loadBitmap(uri: Uri, maxPixels: Int = MAX_BITMAP_PIXELS): Bitmap? {
        try {
            // Decode bounds only (no memory allocation)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            reactContext.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                Log.e(TAG, "Failed to decode image bounds")
                return null
            }
            
            // Calculate sample size for memory efficiency
            // Use Long arithmetic to prevent integer overflow for very large images
            // (e.g., 50000×50000 = 2.5 billion pixels exceeds Int.MAX_VALUE)
            val currentPixels = options.outWidth.toLong() * options.outHeight.toLong()
            options.inSampleSize = if (currentPixels > maxPixels) {
                calculateInSampleSize(options.outWidth, options.outHeight, maxPixels)
            } else {
                1
            }
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            
            Log.d(TAG, "Loading bitmap: ${options.outWidth}x${options.outHeight}, sampleSize=${options.inSampleSize}")
            
            // Decode with sample size
            val originalBitmap = reactContext.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (originalBitmap == null) {
                Log.e(TAG, "Failed to decode bitmap")
                return null
            }

            // Handle Rotation
            // postRotate() rotates around origin (0,0), so we need to translate
            // the rotated image back into the visible canvas area
            val rotation = getRotation(uri)
            val matrix = Matrix()
            when (rotation) {
                90 -> {
                    matrix.postRotate(90f)
                    matrix.postTranslate(originalBitmap.height.toFloat(), 0f)
                }
                180 -> {
                    matrix.postRotate(180f)
                    matrix.postTranslate(originalBitmap.width.toFloat(), originalBitmap.height.toFloat())
                }
                270 -> {
                    matrix.postRotate(270f)
                    matrix.postTranslate(0f, originalBitmap.width.toFloat())
                }
            }

            // Handle Transparency: Draw on white background
            val newBitmap = Bitmap.createBitmap(
                if (rotation % 180 == 0) originalBitmap.width else originalBitmap.height,
                if (rotation % 180 == 0) originalBitmap.height else originalBitmap.width,
                Bitmap.Config.ARGB_8888
            )
            
            val canvas = Canvas(newBitmap)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(originalBitmap, matrix, null)
            
            // Recycle original bitmap as it's no longer needed
            originalBitmap.recycle()
            
            return newBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap", e)
            return null
        }
    }
    
    /**
     * Calculate inSampleSize to downsample image to target pixel count.
     * Uses power of 2 sampling for efficient decoding.
     */
    private fun calculateInSampleSize(width: Int, height: Int, maxPixels: Int): Int {
        // Use Long arithmetic to prevent integer overflow for very large images
        val pixels = width.toLong() * height.toLong()
        var inSampleSize = 1
        while ((pixels / (inSampleSize * inSampleSize)) > maxPixels) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun getRotation(uri: Uri): Int {
        try {
            val exifInterface = if (uri.scheme == "file") {
                uri.path?.let { ExifInterface(it) }
            } else {
                // For content://, use inputStream (API 24+) or fallback to 0
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    reactContext.contentResolver.openInputStream(uri)?.use { 
                        ExifInterface(it) 
                    }
                } else {
                    null
                }
            }

            return when (exifInterface?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read Exif", e)
            return 0
        }
    }


    /**
     * Release camera resources asynchronously.
     * Full cleanup INCLUDING executor shutdown.
     * Executor shutdown is scheduled on main thread to avoid deadlock.
     */
    fun releaseCamera() {
        Log.d(TAG, "🧹 releaseCamera() called")
        
        // Stop scanning first using atomic operation
        if (isScanning.compareAndSet(true, false)) {
            scanCallbackRef.set(null)
        }

        // Clear the analyzer immediately to stop processing new frames
        imageAnalysis?.clearAnalyzer()
        
        // Schedule cleanup on main thread (non-blocking)
        ContextCompat.getMainExecutor(reactContext).execute {
            performFullCleanup()
        }
    }
    
    /**
     * Full cleanup on main thread including executor shutdown.
     * Guards against race condition: if scanning restarted, skip cleanup.
     */
    private fun performFullCleanup(retryCount: Int = 0) {
        try {
            // Race condition guard: if scanning restarted, skip this cleanup
            if (isScanning.get()) {
                Log.d(TAG, "⏭️ Skipping cleanup - scanning was restarted")
                return
            }
            
            // Handle binding in progress with retry and max retry limit
            if (isBinding) {
                if (retryCount >= MAX_CLEANUP_RETRIES) {
                    Log.e(TAG, "❌ Max cleanup retries ($MAX_CLEANUP_RETRIES) reached, forcing cleanup")
                    // Force reset isBinding flag and continue with cleanup
                    cameraBindLock.withLock {
                        isBinding = false
                    }
                } else {
                    Log.d(TAG, "⏳ Binding in progress, scheduling delayed cleanup (retry ${retryCount + 1}/$MAX_CLEANUP_RETRIES)")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        performFullCleanup(retryCount + 1)
                    }, 100)
                    return
                }
            }
            
            // Unbind camera
            cameraBindLock.withLock {
                cameraProvider?.unbindAll()
            }
            
            // Clear all references
            cameraProvider = null
            cameraControl = null
            imageAnalysis = null
            preview = null
            previewView = null
            scanCallbackRef.set(null)
            
            // Close the barcode scanner
            scanner?.close()
            scanner = null
            
            // Shutdown executor on a separate thread to avoid blocking main thread
            Thread {
                executorLock.withLock {
                    if (!cameraExecutor.isShutdown) {
                        cameraExecutor.shutdown()
                        try {
                            if (!cameraExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                                cameraExecutor.shutdownNow()
                                Log.w(TAG, "⚠️ Executor forced shutdown")
                            }
                        } catch (e: InterruptedException) {
                            cameraExecutor.shutdownNow()
                            Thread.currentThread().interrupt()
                        }
                    }
                }
                Log.d(TAG, "✅ Camera fully released (including executor)")
            }.start()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error releasing camera: ${e.message}", e)
        }
    }
}
