package com.pushpendersingh.reactnativescanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
    
    // AtomicBoolean for lock-free scanning flag
    private val isScanning = AtomicBoolean(false)
    // AtomicReference for thread-safe callback
    private val scanCallbackRef = AtomicReference<((WritableArray) -> Unit)?>(null)
    
    // Lock for synchronizing camera binding operations
    private val cameraBindLock = ReentrantLock()
    // Flag to prevent concurrent binding
    @Volatile
    private var isBinding = false
    // Lock for executor lifecycle management
    private val executorLock = ReentrantLock()

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
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
                cameraExecutor = Executors.newSingleThreadExecutor()
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

    fun releaseCamera() {
        try {
            Log.d(TAG, "Releasing camera resources...")
            
            // Stop scanning first using atomic operation
            if (isScanning.compareAndSet(true, false)) {
                scanCallbackRef.set(null)
            }

            // Wait for binding to complete WITHOUT holding the lock
            // This prevents deadlock when startScanning() is called during release
            var attempts = 0
            while (isBinding && attempts < 100) { // Max wait: 5 seconds
                Log.d(TAG, "⏳ Waiting for camera binding to complete before release...")
                Thread.sleep(50)
                attempts++
            }
            
            if (isBinding) {
                Log.w(TAG, "⚠️ Binding still in progress after 5s, forcing release")
            }

            // Now safely unbind with lock (binding should be complete)
            cameraBindLock.withLock {
                // Double-check binding state and unbind
                if (isBinding) {
                    Log.w(TAG, "⚠️ Binding flag still set, unbinding anyway")
                }
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
        
            // Use dedicated executor lock for shutdown
            // This prevents race with ensureExecutor() and getExecutorSafely()
            executorLock.withLock {
                if (!cameraExecutor.isShutdown) {
                    cameraExecutor.shutdown()
                    try {
                        if (!cameraExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                            cameraExecutor.shutdownNow()
                            Log.w(TAG, "⚠️ Executor did not terminate gracefully, forced shutdown")
                        }
                    } catch (e: InterruptedException) {
                        cameraExecutor.shutdownNow()
                        Thread.currentThread().interrupt()
                        Log.w(TAG, "⚠️ Executor shutdown interrupted")
                    }
                }
            }
        
            Log.d(TAG, "✅ Camera resources released successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error releasing camera: ${e.message}", e)
        }
    }
}
