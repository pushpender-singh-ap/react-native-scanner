package com.pushpendersingh.reactnativescanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.view.Choreographer
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.ReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.EventDispatcher
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ReactNativeScannerView(context: Context) :  LinearLayout(context) {

  private var preview: PreviewView
  private var mSurfacePreview: Preview? = null
  private var mCameraProvider: ProcessCameraProvider? = null
  private var analysisUseCase: ImageAnalysis = ImageAnalysis.Builder()
    .build()

  private lateinit var options: BarcodeScannerOptions
  private lateinit var scanner: BarcodeScanner

  private var isCameraRunning: Boolean = false
  private var pauseAfterCapture: Boolean = false
  private var isActive: Boolean = false

  companion object {
    private val REQUIRED_PERMISSIONS =
      mutableListOf(
        Manifest.permission.CAMERA
      ).toTypedArray()
  }

  init {
    val linearLayoutParams = ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT,
      ViewGroup.LayoutParams.WRAP_CONTENT
    )
    layoutParams = linearLayoutParams
    orientation = VERTICAL

    preview = PreviewView(context)
    preview.layoutParams = ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    addView(preview)

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

  fun setUpCamera() {
    if (allPermissionsGranted()) {
      startCamera()
    }

    // newSingleThreadExecutor() will let us perform analysis on a single worker thread
    val cameraExecutor = Executors.newSingleThreadExecutor()

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

    analysisUseCase.setAnalyzer(
      cameraExecutor
    ) { imageProxy ->
      processImageProxy(scanner, imageProxy)
    }
  }

  @SuppressLint("UnsafeOptInUsageError")
  private fun processImageProxy(
    barcodeScanner: BarcodeScanner,
    imageProxy: ImageProxy
  ) {
    imageProxy.image?.let { image ->
      val inputImage =
        InputImage.fromMediaImage(
          image,
          imageProxy.imageInfo.rotationDegrees
        )

      if (!isCameraRunning) {
        return;
      }

      barcodeScanner.process(inputImage)
        .addOnSuccessListener { barcodeList ->
          // mCameraProvider?.unbindAll() // this line will stop the camera from scanning after the first scan

          if (barcodeList.isNotEmpty()) {
            if (pauseAfterCapture) {
              pausePreview()
            }

            val reactContext = context as ReactContext
            val surfaceId = UIManagerHelper.getSurfaceId(reactContext)
            val eventDispatcher: EventDispatcher? =
              UIManagerHelper.getEventDispatcherForReactTag(
                reactContext, id
              )

            barcodeList.forEach { barcode ->
              barcode?.let { code ->
                eventDispatcher?.dispatchEvent(code.cornerPoints?.let { cornerPoints ->
                  code.boundingBox?.let { bounds ->
                    ReactNativeScannerViewEvent(surfaceId, id, code.rawValue
                      ?: "", bounds, cornerPoints, code.format)
                  }
                })
              }
            }
          }
        }
        .addOnFailureListener {
          // This failure will happen if the barcode scanning model
          // fails to download from Google Play Services
        }.addOnCompleteListener {
          // When the image is from CameraX analysis use case, must
          // call image.close() on received images when finished
          // using them. Otherwise, new images may not be received
          // or the camera may stall.
          imageProxy.image?.close()
          imageProxy.close()
        }
    }
  }

  private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(
      context, it
    ) == PackageManager.PERMISSION_GRANTED
  }

  private fun startCamera() {
    val reactContext = context as ReactContext
    val cameraProviderFuture = ProcessCameraProvider.getInstance(reactContext)

    cameraProviderFuture.addListener({
      // Used to bind the lifecycle of cameras to the lifecycle owner
      val cameraProvider = cameraProviderFuture.get()
      mCameraProvider = cameraProvider

      // Preview
      val surfacePreview = Preview.Builder()
        .build()
        .also {
          it.setSurfaceProvider(preview.surfaceProvider)
        }
      mSurfacePreview = surfacePreview

      // Select back camera as a default
      val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

      isCameraRunning = true

      try {
        // Unbind use cases before rebinding
        cameraProvider.unbindAll()

        // Bind use cases to camera
        cameraProvider.bindToLifecycle(
          (reactContext.currentActivity as AppCompatActivity),
          cameraSelector,
          surfacePreview,
          analysisUseCase
        )
      } catch (exc: Exception) {
        isCameraRunning = false
      }
    }, ContextCompat.getMainExecutor(context))
  }

  fun setPauseAfterCapture(value: Boolean) {
    pauseAfterCapture = value
  }

  fun setIsActive(value: Boolean) {
    isActive = value
  }

  fun pausePreview() {
    if (isCameraRunning) {
      isCameraRunning = false
      mCameraProvider?.unbind(analysisUseCase)
    }
  }

  fun resumePreview() {
    if (!isCameraRunning) {
      isCameraRunning = true

      try {
        val reactContext = context as ReactContext
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        // Bind use cases to camera
        mCameraProvider?.bindToLifecycle(
          (reactContext.currentActivity as AppCompatActivity),
          cameraSelector,
          analysisUseCase
        )
      } catch (exc: Exception) {
        isCameraRunning = false
      }
    }
  }
}
