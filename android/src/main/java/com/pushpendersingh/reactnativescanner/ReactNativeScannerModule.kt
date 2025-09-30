package com.pushpendersingh.reactnativescanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.module.annotations.ReactModule

@ReactModule(name = ReactNativeScannerModule.NAME)
class ReactNativeScannerModule(reactContext: ReactApplicationContext) :
  NativeReactNativeScannerSpec(reactContext) {

  private val cameraManager: CameraManager = CameraManager(reactContext)

  override fun getName(): String {
    return NAME
  }

  // Expose camera manager for ViewManager
  fun getCameraManager(): CameraManager {
    return cameraManager
  }

  @ReactMethod
  override fun startScanning(promise: Promise) {
    try {
      if (!cameraManager.hasCameraPermission()) {
        promise.reject("PERMISSION_DENIED", "Camera permission not granted")
        return
      }

      cameraManager.startScanning { result ->
        // Send barcode result as event to JavaScript
        reactApplicationContext
          .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
          .emit("onBarcodeScanned", result)
      }
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("START_SCANNING_ERROR", e.message, e)
    }
  }

  @ReactMethod
  override fun stopScanning(promise: Promise) {
    try {
      cameraManager.stopScanning()
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("STOP_SCANNING_ERROR", e.message, e)
    }
  }

  @ReactMethod
  override fun enableFlashlight(promise: Promise) {
    try {
      cameraManager.enableFlashlight()
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("FLASHLIGHT_ERROR", e.message, e)
    }
  }

  @ReactMethod
  override fun disableFlashlight(promise: Promise) {
    try {
      cameraManager.disableFlashlight()
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("FLASHLIGHT_ERROR", e.message, e)
    }
  }

  @ReactMethod
  override fun releaseCamera(promise: Promise) {
    try {
      cameraManager.releaseCamera()
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("RELEASE_CAMERA_ERROR", e.message, e)
    }
  }

  @ReactMethod
  override fun hasCameraPermission(promise: Promise) {
    try {
      val hasPermission = cameraManager.hasCameraPermission()
      promise.resolve(hasPermission)
    } catch (e: Exception) {
      promise.reject("PERMISSION_CHECK_ERROR", e.message, e)
    }
  }

  @ReactMethod
  override fun requestCameraPermission(promise: Promise) {
    try {
      val currentActivity = reactApplicationContext.currentActivity
      if (currentActivity == null) {
        promise.reject("NO_ACTIVITY", "Current activity is null")
        return
      }

      if (cameraManager.hasCameraPermission()) {
        promise.resolve(true)
        return
      }

      // Request permission
      ActivityCompat.requestPermissions(
        currentActivity,
        arrayOf(Manifest.permission.CAMERA),
        CAMERA_PERMISSION_REQUEST_CODE
      )
      
      // Note: In a production app, you should handle the permission result
      // through onRequestPermissionsResult and use a promise callback
      promise.resolve(false)
    } catch (e: Exception) {
      promise.reject("PERMISSION_REQUEST_ERROR", e.message, e)
    }
  }

  @ReactMethod
  override fun addListener(eventName: String) {
    // Required for event emitters
  }

  @ReactMethod
  override fun removeListeners(count: Double) {
    // Required for event emitters
  }

  override fun invalidate() {
    super.invalidate()
    cameraManager.releaseCamera()
  }

  companion object {
    const val NAME = "ReactNativeScanner"
    private const val CAMERA_PERMISSION_REQUEST_CODE = 100
  }
}
