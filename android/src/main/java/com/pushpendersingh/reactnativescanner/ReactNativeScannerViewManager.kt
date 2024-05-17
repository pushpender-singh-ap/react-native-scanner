package com.pushpendersingh.reactnativescanner

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.common.MapBuilder
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.viewmanagers.ReactNativeScannerViewManagerInterface
import com.facebook.react.viewmanagers.ReactNativeScannerViewManagerDelegate

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

  override fun enableFlashlight(view: ReactNativeScannerView?) {
    view?.enableFlashlight()
  }

  override fun disableFlashlight(view: ReactNativeScannerView?) {
    view?.disableFlashlight()
  }

  override fun releaseCamera(view: ReactNativeScannerView?) {
    view?.releaseCamera()
  }

  override fun createViewInstance(reactContext: ThemedReactContext): ReactNativeScannerView {
    val reactnativeScannerView = ReactNativeScannerView(mCallerContext)
    reactnativeScannerView.setUpCamera(mCallerContext)
    return reactnativeScannerView
  }

  companion object {
    const val NAME = "ReactNativeScannerView"
  }

  override fun getExportedCustomDirectEventTypeConstants(): Map<String?, Any> {
    return MapBuilder.of(
      "onQrScanned",
      MapBuilder.of("registrationName", "onQrScanned")
    )
  }
}
