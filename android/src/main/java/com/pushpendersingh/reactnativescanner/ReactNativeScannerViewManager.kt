package com.pushpendersingh.reactnativescanner

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.common.MapBuilder
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.viewmanagers.ReactNativeScannerViewManagerInterface
import com.facebook.react.uimanager.annotations.ReactProp
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

  @ReactProp(name = "pauseAfterCapture")
  override fun setPauseAfterCapture(view: ReactNativeScannerView?, value: Boolean) {
    view?.setPauseAfterCapture(value)
  }

  @ReactProp(name = "isActive")
  override fun setIsActive(view: ReactNativeScannerView?, value: Boolean) {
    view?.setIsActive(value)
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

  override fun pausePreview(view: ReactNativeScannerView?) {
    view?.pausePreview()
  }

  override fun resumePreview(view: ReactNativeScannerView?) {
    view?.resumePreview()
  }

  override fun createViewInstance(reactContext: ThemedReactContext): ReactNativeScannerView {
    val reactnativeScannerView = ReactNativeScannerView(mCallerContext)
    reactnativeScannerView.setUpCamera(mCallerContext)
    return reactnativeScannerView
  }

  companion object {
    const val NAME = "ReactNativeScannerView"

    const val COMMAND_PAUSE_PREVIEW = 1
    const val COMMAND_RESUME_PREVIEW = 2
  }

  override fun getExportedCustomDirectEventTypeConstants(): Map<String?, Any> {
    return MapBuilder.of(
      "onQrScanned",
      MapBuilder.of("registrationName", "onQrScanned")
    )
  }

  override fun getCommandsMap(): MutableMap<String, Int> {
    val map = mutableMapOf<String, Int>()
    map["pausePreview"] = COMMAND_PAUSE_PREVIEW
    map["resumePreview"] = COMMAND_RESUME_PREVIEW
    return map
  }

  override fun receiveCommand(root: ReactNativeScannerView, commandId: String?, args: ReadableArray?) {
    when (commandId) {
      "pausePreview" -> root.pausePreview()
      "resumePreview" -> root.resumePreview()
      else -> {
        println("Unsupported Command")
      }
    }

    super.receiveCommand(root, commandId, args)
  }
}
