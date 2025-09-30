package com.pushpendersingh.reactnativescanner

import android.view.View
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

@ReactModule(name = ReactNativeScannerViewManager.NAME)
class ReactNativeScannerViewManager : SimpleViewManager<ReactNativeScannerView>() {

    private var cameraManager: CameraManager? = null

    override fun getName(): String {
        return NAME
    }

    override fun createViewInstance(reactContext: ThemedReactContext): ReactNativeScannerView {
        val view = ReactNativeScannerView(reactContext)
        
        // Get or create camera manager from the module
        val scannerModule = reactContext.catalystInstance
            ?.getNativeModule("ReactNativeScanner") as? ReactNativeScannerModule
        
        cameraManager = scannerModule?.getCameraManager()
        cameraManager?.let { view.setCameraManager(it) }
        
        return view
    }

    @ReactProp(name = "active")
    fun setActive(view: ReactNativeScannerView, active: Boolean) {
        // Could be used to pause/resume scanning
    }

    override fun getExportedCustomDirectEventTypeConstants(): Map<String, Any>? {
        return mapOf(
            "onBarcodeScanned" to mapOf("registrationName" to "onBarcodeScanned"),
            "onError" to mapOf("registrationName" to "onError")
        )
    }

    override fun onDropViewInstance(view: ReactNativeScannerView) {
        super.onDropViewInstance(view)
        // View cleanup is handled in the view's onDetachedFromWindow
    }

    companion object {
        const val NAME = "ReactNativeScannerView"
    }
}
