package com.pushpendersingh.reactnativescanner

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.common.MapBuilder
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.viewmanagers.ReactNativeScannerViewManagerDelegate
import com.facebook.react.viewmanagers.ReactNativeScannerViewManagerInterface

@ReactModule(name = ReactNativeScannerViewManager.NAME)
class ReactNativeScannerViewManager(private val mCallerContext: ReactApplicationContext) :
  SimpleViewManager<ReactNativeScannerView>(), ReactNativeScannerViewManagerInterface<ReactNativeScannerView?> {

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

  override fun createViewInstance(reactContext: ThemedReactContext): ReactNativeScannerView {
    val scannerView = ReactNativeScannerView(mCallerContext)
    scannerView.setUpCamera(mCallerContext)
    return scannerView
  }

  companion object {
    const val NAME = "ReactNativeScannerView"
  }

  override fun getExportedCustomDirectEventTypeConstants(): Map<String?, Any> {
    return MapBuilder.of(
      "topOnQrScanned",
      MapBuilder.of("registrationName", "onQrScanned")
    )
  }

  @ReactProp(name = "pauseAfterCapture")
  override fun setPauseAfterCapture(view: ReactNativeScannerView?, value: Boolean) {
    view?.setPauseAfterCapture(value)
  }

  override fun pausePreview(view: ReactNativeScannerView?) {
    view?.pauseCamera()
  }

  override fun resumePreview(view: ReactNativeScannerView?) {
    view?.resumeCamera()
  }
}
