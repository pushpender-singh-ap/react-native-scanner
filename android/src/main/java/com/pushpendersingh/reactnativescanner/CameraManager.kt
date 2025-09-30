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
import com.facebook.react.bridge.WritableMap
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(private val reactContext: ReactApplicationContext) {

    private val TAG = "CameraManager"
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var scanner: BarcodeScanner? = null
    private var cameraControl: CameraControl? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var previewView: PreviewView? = null
    
    @Volatile
    private var isScanning: Boolean = false
    private var scanCallback: ((WritableMap) -> Unit)? = null

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
        if (isScanning) {
            // If already scanning, rebind with preview
            bindCameraUseCases()
        }
    }

    @Synchronized
    fun startScanning(callback: (WritableMap) -> Unit) {
        if (!hasCameraPermission()) {
            throw SecurityException("Camera permission not granted")
        }

        if (isScanning) {
            Log.w(TAG, "Scanning already in progress")
            return
        }

        scanCallback = callback
        initializeCamera()
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
                isScanning = false
            }
        }, ContextCompat.getMainExecutor(reactContext))
    }

    private fun bindCameraUseCases() {
        Log.d(TAG, "Binding camera use cases...")
        val currentActivity = reactContext.currentActivity as? AppCompatActivity
        if (currentActivity == null) {
            Log.e(TAG, "❌ Current activity is not available")
            return
        }

        try {
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

            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (!isScanning) {
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
            isScanning = true
            Log.d(TAG, "Camera successfully bound and scanning started (preview: ${preview != null})")
        } catch (exc: Exception) {
            Log.e(TAG, "Error binding camera use cases: ${exc.message}", exc)
            isScanning = false
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
                for (barcode in barcodes) {
                    if (!barcode.rawValue.isNullOrEmpty()) {
                        val result = createBarcodeResult(barcode)
                        scanCallback?.invoke(result)
                        break // Process only the first barcode
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

    @Synchronized
    fun stopScanning() {
        if (!isScanning) {
            Log.w(TAG, "Scanning is not in progress")
            return
        }

        try {
            Log.d(TAG, "Stopping scanning...")
            isScanning = false
            scanCallback = null
            
            // Clear the analyzer to stop processing frames
            imageAnalysis?.clearAnalyzer()
            
            // Unbind all use cases from the camera
            cameraProvider?.unbindAll()
            
            // Clear references but don't shut down executor or scanner
            // so they can be reused if scanning starts again
            cameraControl = null
            imageAnalysis = null
            preview = null
            
            Log.d(TAG, "✅ Scanning stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scanning: ${e.message}", e)
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
            
            // Stop scanning first
            if (isScanning) {
                stopScanning()
            }
            
            // Unbind all camera use cases
            cameraProvider?.unbindAll()
            
            // Clear all references
            cameraProvider = null
            cameraControl = null
            imageAnalysis = null
            preview = null
            previewView = null
            scanCallback = null
            
            // Close the barcode scanner
            scanner?.close()
            scanner = null
            
            // Note: We don't shutdown cameraExecutor here because it's a singleton
            // and we want to reuse it if scanning starts again
            
            Log.d(TAG, "✅ Camera resources released successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error releasing camera: ${e.message}", e)
        }
    }
}
