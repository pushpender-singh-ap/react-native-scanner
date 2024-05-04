package com.pushpendersingh.reactnativescanner

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.RCTModernEventEmitter


class ReactNativeScannerViewEvent(viewId: Int, private val qrValue: String): Event<ReactNativeScannerViewEvent>(viewId) {

    override fun getEventName(): String {
        return "onQrScanned"
    }

    override fun dispatchModern(rctEventEmitter: RCTModernEventEmitter) {
        super.dispatchModern(rctEventEmitter) // if we don't call this, the react native part won't receive the event but because of this line event call two times
        rctEventEmitter.receiveEvent(
            -1,
            viewTag, eventName,
            Arguments.createMap()
        )
    }

    override fun getEventData(): WritableMap {
        val event: WritableMap = Arguments.createMap()
        event.putString("value", qrValue)
        return event
    }

}