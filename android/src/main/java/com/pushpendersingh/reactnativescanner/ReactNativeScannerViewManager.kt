package com.pushpendersingh.reactnativescanner

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.viewmanagers.ReactNativeScannerViewManagerInterface
import com.facebook.react.viewmanagers.ReactNativeScannerViewManagerDelegate

@ReactModule(name = ReactNativeScannerViewManager.NAME)
class ReactNativeScannerViewManager(private val mCallerContext: ReactApplicationContext) :
  SimpleViewManager<ReactNativeScannerView>(),
  ReactNativeScannerViewManagerInterface<ReactNativeScannerView> {

  private val mDelegate: ViewManagerDelegate<ReactNativeScannerView>

  init {
    mDelegate = ReactNativeScannerViewManagerDelegate(this)
  }

  override fun getDelegate(): ViewManagerDelegate<ReactNativeScannerView>? {
    return mDelegate
  }

  override fun getName(): String {
    return NAME
  }

  @ReactProp(name = "pauseAfterCapture")
  override fun setPauseAfterCapture(view: ReactNativeScannerView, value: Boolean) {
    view.setPauseAfterCapture(value)
  }

  @ReactProp(name = "showBox")
  override fun setShowBox(view: ReactNativeScannerView, value: Boolean) {
    view.setShowBox(value)
  }

  @ReactProp(name = "isActive")
  override fun setIsActive(view: ReactNativeScannerView, value: Boolean) {
    view.setIsActive(value)
  }

  override fun enableFlashlight(view: ReactNativeScannerView) {
    view.enableFlashlight()
  }

  override fun disableFlashlight(view: ReactNativeScannerView) {
    view.disableFlashlight()
  }

  override fun releaseCamera(view: ReactNativeScannerView) {
    view.releaseCamera()
  }

  override fun stopScanning(view: ReactNativeScannerView) {
    view.stopScanning()
  }

  override fun resumeScanning(view: ReactNativeScannerView) {
    view.resumeScanning()
  }

  override fun startCamera(view: ReactNativeScannerView) {
    val reactAppContext = view.context as? ReactApplicationContext
    reactAppContext?.let {
      view.setUpCamera(it)
    }
  }

  override fun createViewInstance(reactContext: ThemedReactContext): ReactNativeScannerView {
    val reactnativeScannerView = ReactNativeScannerView(mCallerContext)
    reactnativeScannerView.setUpCamera(mCallerContext)
    return reactnativeScannerView
  }

  companion object {
    const val NAME = "ReactNativeScannerView"
  }
}
