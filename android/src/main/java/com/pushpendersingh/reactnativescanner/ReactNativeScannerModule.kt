package com.pushpendersingh.reactnativescanner

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableArray
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener
import com.pushpendersingh.reactnativescanner.NativeReactNativeScannerSpec

@ReactModule(name = ReactNativeScannerModule.NAME)
class ReactNativeScannerModule(reactContext: ReactApplicationContext) :
  NativeReactNativeScannerSpec(reactContext) {

  private val cameraManager: CameraManager = CameraManager(reactContext)
  private var permissionPromise: Promise? = null
  
  // Track permission request time for timeout-based cleanup
  private var permissionRequestTime: Long = 0
  private val PERMISSION_TIMEOUT_MS = 60_000L  // 1 minute timeout
  
  // Lifecycle listener to clean up permission promise on activity destroy
  private val lifecycleEventListener = object : com.facebook.react.bridge.LifecycleEventListener {
    override fun onHostResume() {}
    override fun onHostPause() {}
    override fun onHostDestroy() {
      permissionPromise?.reject("ACTIVITY_DESTROYED", "Activity was destroyed before permission result")
      permissionPromise = null
    } 
  }
  
  init {
    reactContext.addLifecycleEventListener(lifecycleEventListener)
  }
  
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
        emitOnBarcodeScanned(result)
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

      // Clear stale permission promise based on timeout
      if (permissionPromise != null) {
        val elapsed = System.currentTimeMillis() - permissionRequestTime
        if (elapsed > PERMISSION_TIMEOUT_MS) {
          android.util.Log.w(NAME, "Clearing stale permission promise after ${elapsed}ms")
          permissionPromise?.reject("PERMISSION_TIMEOUT", "Permission request timed out")
          permissionPromise = null
        }
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
      permissionRequestTime = System.currentTimeMillis()  // Track request time
      
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
  override fun scanImage(imageUri: String, promise: Promise) {
    try {
      cameraManager.scanImage(imageUri) { result ->
        promise.resolve(result)
      }
    } catch (e: Exception) {
      promise.reject("SCAN_IMAGE_ERROR", e.message, e)
    }
  }

  override fun invalidate() {
    super.invalidate()
    permissionPromise?.reject("MODULE_INVALIDATED", "Scanner module was invalidated")
    permissionPromise = null
    reactApplicationContext.removeLifecycleEventListener(lifecycleEventListener)
    cameraManager.releaseCamera()
  }

  companion object {
    const val NAME = "ReactNativeScanner"
    private const val CAMERA_PERMISSION_REQUEST_CODE = 100
  }
}
