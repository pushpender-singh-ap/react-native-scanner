package com.pushpendersingh.reactnativescanner

import com.facebook.react.uimanager.events.Event

class ReactNativeScannerViewEvent(
    surfaceId: Int,
    viewId: Int,
    ) : Event<ReactNativeScannerViewEvent>(surfaceId, viewId) {

    override fun getEventName(): String {
        return "onQrScanned"
    }
}
