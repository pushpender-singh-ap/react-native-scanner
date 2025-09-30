package com.pushpendersingh.reactnativescanner

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener

@ReactModule(name = ReactNativeScannerModule.NAME)
class ReactNativeScannerModule(reactContext: ReactApplicationContext) :
  NativeReactNativeScannerSpec(reactContext) {

  private val cameraManager: CameraManager = CameraManager(reactContext)
  private var permissionPromise: Promise? = null
  
  private val permissionListener = PermissionListener { requestCode, permissions, grantResults ->
    if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
      // Validate that we're handling the correct permission
      val permissionGranted = permissions.isNotEmpty() &&
                               permissions[0] == Manifest.permission.CAMERA &&
                               grantResults.isNotEmpty() && 
                               grantResults[0] == PackageManager.PERMISSION_GRANTED
      
      permissionPromise?.resolve(permissionGranted)
      permissionPromise = null
      return@PermissionListener true
    }
    false
  }

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

      // Check if there's already a pending permission request
      if (permissionPromise != null) {
        promise.reject(
          "PERMISSION_REQUEST_IN_PROGRESS", 
          "A camera permission request is already in progress"
        )
        return
      }

      // Store promise to be resolved in permission callback
      permissionPromise = promise
      
      // Request permission using PermissionAwareActivity
      val permissionAwareActivity = currentActivity as? PermissionAwareActivity
      if (permissionAwareActivity != null) {
        permissionAwareActivity.requestPermissions(
          arrayOf(Manifest.permission.CAMERA),
          CAMERA_PERMISSION_REQUEST_CODE,
          permissionListener
        )
      } else {
        promise.reject("NO_PERMISSION_AWARE_ACTIVITY", "Current activity does not implement PermissionAwareActivity")
        permissionPromise = null
      }
    } catch (e: Exception) {
      promise.reject("PERMISSION_REQUEST_ERROR", e.message, e)
      permissionPromise = null
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
    permissionPromise = null
    cameraManager.releaseCamera()
  }

  companion object {
    const val NAME = "ReactNativeScanner"
    private const val CAMERA_PERMISSION_REQUEST_CODE = 100
  }
}
