package com.pushpendersingh.reactnativescanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.core.CameraControl
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import android.util.Log

class ReactNativeScannerView(context: Context) : LinearLayout(context) {

    private val TAG = "ReactNativeScannerView"

    private var preview: PreviewView
    private var mCameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var options: BarcodeScannerOptions
    private lateinit var scanner: BarcodeScanner
    private lateinit var cameraControl: CameraControl
    private lateinit var lifecycleCameraController: LifecycleCameraController
    private lateinit var overlay: BarcodeOverlayView
    private var showBoxFromUser = false

    @Volatile
    private var isCameraRunning: Boolean = false
    @Volatile
    private var pauseAfterCapture: Boolean = false
    @Volatile
    private var isActive: Boolean = true

    private lateinit var surfacePreview: Preview
    private lateinit var imageAnalysis: ImageAnalysis
    private lateinit var mlKitAnalyzer: MlKitAnalyzer

    // Handler and Runnable for hiding the box after delay
    private val handler = Handler(Looper.getMainLooper())
    private val hideBoxRunnable = Runnable {
        overlay.setShowBox(false)
        overlay.setRect(null, false)
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        orientation = VERTICAL

        preview = PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        addView(preview)

        overlay = BarcodeOverlayView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        addView(overlay)

        setupLayoutHack()
        manuallyLayoutChildren()
    }

    private fun setupLayoutHack() {
        Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                manuallyLayoutChildren()
                viewTreeObserver.dispatchOnGlobalLayout()
                Choreographer.getInstance().postFrameCallback(this)
            }
        })
    }

    private fun manuallyLayoutChildren() {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(
                MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
            )
            child.layout(0, 0, child.measuredWidth, child.measuredHeight)
        }
    }

    fun setUpCamera(reactApplicationContext: ReactApplicationContext) {
        if (allPermissionsGranted()) {
            startCamera(reactApplicationContext)
        }

        options = BarcodeScannerOptions.Builder()
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

        lifecycleCameraController = LifecycleCameraController(context).apply {
            bindToLifecycle(reactApplicationContext.currentActivity as AppCompatActivity)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }

        preview.controller = lifecycleCameraController

        mlKitAnalyzer = MlKitAnalyzer(
            listOf(scanner),
            CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED,
            ContextCompat.getMainExecutor(context)
        ) { result ->
            if (!isCameraRunning || !isActive) return@MlKitAnalyzer

            val barcodes = result?.getValue(scanner).orEmpty()
            val barcode = barcodes.firstOrNull { !it.rawValue.isNullOrEmpty() }
            barcode?.boundingBox?.let { bounds ->
                if (showBoxFromUser) {
                    updateOverlay(bounds, true)
                    handler.removeCallbacks(hideBoxRunnable)
                    handler.postDelayed(hideBoxRunnable, 1000)
                }
                sendBarcodeEvent(barcode.rawValue ?: "", bounds, showBoxFromUser, barcode.format)

                if (pauseAfterCapture) {
                    stopScanning()
                }
            }
        }

        imageAnalysis = ImageAnalysis.Builder()
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, mlKitAnalyzer)
            }

        lifecycleCameraController.setImageAnalysisAnalyzer(
            cameraExecutor,
            mlKitAnalyzer
        )
        lifecycleCameraController.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
    }

    private fun updateOverlay(rect: Rect, showBox: Boolean) {
        overlay.setRect(rect, showBox)
    }

    private fun sendBarcodeEvent(data: String, bounds: Rect, showBox: Boolean, type: Int) {
        val reactContext = context as ReactContext
        val event = Arguments.createMap().apply {
            putString("data", data)
            putString("type", when (type) {
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
            })
            putBoolean("showBox", showBox)
            putMap("bounds", Arguments.createMap().apply {
                putDouble("width", bounds.width().toDouble())
                putDouble("height", bounds.height().toDouble())
                putMap("origin", Arguments.createMap().apply {
                    putMap("topLeft", Arguments.createMap().apply {
                        putDouble("x", bounds.left.toDouble())
                        putDouble("y", bounds.top.toDouble())
                    })
                    putMap("bottomLeft", Arguments.createMap().apply {
                        putDouble("x", bounds.left.toDouble())
                        putDouble("y", bounds.bottom.toDouble())
                    })
                    putMap("bottomRight", Arguments.createMap().apply {
                        putDouble("x", bounds.right.toDouble())
                        putDouble("y", bounds.bottom.toDouble())
                    })
                    putMap("topRight", Arguments.createMap().apply {
                        putDouble("x", bounds.right.toDouble())
                        putDouble("y", bounds.top.toDouble())
                    })
                })
            })
        }
        reactContext.getJSModule(RCTEventEmitter::class.java).receiveEvent(id, "onQrScanned", event)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera(reactApplicationContext: ReactApplicationContext) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                mCameraProvider = cameraProvider

                surfacePreview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(preview.surfaceProvider)
                    }

                imageAnalysis = ImageAnalysis.Builder()
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, mlKitAnalyzer)
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                isCameraRunning = true

                cameraProvider.unbindAll()

                val camera = cameraProvider.bindToLifecycle(
                    reactApplicationContext.currentActivity as AppCompatActivity,
                    cameraSelector,
                    surfacePreview,
                    imageAnalysis
                )
                cameraControl = camera.cameraControl
            } catch (exc: Exception) {
                Log.e(TAG, "Error starting camera: ${exc.message}")
                isCameraRunning = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun enableFlashlight() {
        try {
            cameraControl.enableTorch(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling flashlight: ${e.message}")
        }
    }

    fun disableFlashlight() {
        try {
            cameraControl.enableTorch(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling flashlight: ${e.message}")
        }
    }

    fun releaseCamera() {
        try {
            cameraExecutor.shutdown()
            isCameraRunning = false
            imageAnalysis.clearAnalyzer()
            lifecycleCameraController.unbind()
            mCameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing camera: ${e.message}")
        }
    }

    fun setPauseAfterCapture(value: Boolean) {
        pauseAfterCapture = value
    }

    fun setIsActive(value: Boolean) {
        isActive = value
    }

    @Synchronized
    fun stopScanning() {
        if (isCameraRunning) {
            try {
                isCameraRunning = false
                imageAnalysis.clearAnalyzer()
                lifecycleCameraController.unbind()
                mCameraProvider?.unbindAll()
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing camera: ${e.message}")
            }
        }
    }

    @Synchronized
    fun resumeScanning() {
        if (!isCameraRunning) {
            try {
                val reactContext = context as ReactContext
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                mCameraProvider?.bindToLifecycle(
                    reactContext.currentActivity as AppCompatActivity,
                    cameraSelector,
                    surfacePreview,
                    imageAnalysis
                )
                isCameraRunning = true
            } catch (exc: Exception) {
                Log.e(TAG, "Error resuming camera: ${exc.message}")
                isCameraRunning = false
            }
        }
    }

    fun setShowBox(showBox: Boolean) {
        showBoxFromUser = showBox
        if (!showBox) {
            overlay.setRect(null, false)
        }
        overlay.setShowBox(showBox)
    }

    inner class BarcodeOverlayView(context: Context) : View(context) {
        private var rect: Rect? = null
        private var showBox: Boolean = false
        private val paint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        fun setRect(rect: Rect?, showBox: Boolean) {
            this.rect = rect
            this.showBox = showBox
            invalidate()
        }

        fun setShowBox(showBox: Boolean) {
            this.showBox = showBox
            if (!showBox) {
                rect = null
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (showBox && rect != null) {
                canvas.drawRect(rect!!, paint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
        releaseCamera()
    }
}