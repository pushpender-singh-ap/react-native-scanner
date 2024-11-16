package com.pushpendersingh.reactnativescanner

import android.graphics.Point
import android.graphics.Rect
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.Event
import com.google.mlkit.vision.barcode.common.Barcode

class ReactNativeScannerViewEvent(
    surfaceId: Int,
    viewId: Int,
    private val qrValue: String,
    private val rect: Rect,
    private val origin: Array<Point>,
    private val type: Int
    ) : Event<ReactNativeScannerViewEvent>(surfaceId, viewId) {

    override fun getEventName(): String {
        return "onQrScanned"
    }

    override fun getEventData(): WritableMap {
        val event: WritableMap = Arguments.createMap()
        val bounds = Arguments.createMap()
        bounds.putArray("origin", getPoints(origin))
        bounds.putInt("width", rect.width())
        bounds.putInt("height", rect.height())

        event.putMap("bounds", bounds)
        event.putString("data", qrValue)
        if (type == Barcode.FORMAT_QR_CODE)
            event.putString("type", "QR_CODE")
        else
            event.putString("type", "UNKNOWN")

        return event
    }

    private fun getPoints(points: Array<Point>): WritableArray {
        val origin: WritableArray = Arguments.createArray()
        for (point in points) {
            val pointData: WritableMap = Arguments.createMap()
            pointData.putInt("x", point.x)
            pointData.putInt("y", point.y)
            origin.pushMap(pointData);
        }
        return origin
    }
}
