package com.pushpendersingh.reactnativescanner

import android.content.Context
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
import com.facebook.react.bridge.ReactContext

class ReactNativeScannerView(context: Context) : FrameLayout(context) {

    private val previewView: PreviewView
    private var cameraManager: CameraManager? = null
    private var layoutCallback: Choreographer.FrameCallback? = null

    init {
        // Create PreviewView
        previewView = PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Set implementation mode for better compatibility
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        addView(previewView)

        // Setup layout hack for proper rendering
        setupLayoutHack()
    }

    private fun setupLayoutHack() {
        layoutCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                manuallyLayoutChildren()
                viewTreeObserver.dispatchOnGlobalLayout()
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        layoutCallback?.let { Choreographer.getInstance().postFrameCallback(it) }
    }
    
    private fun removeLayoutCallback() {
        layoutCallback?.let {
            Choreographer.getInstance().removeFrameCallback(it)
            layoutCallback = null
        }
    }

    private fun manuallyLayoutChildren() {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(
                MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
            )
            child.layout(0, 0, child.measuredWidth, child.measuredHeight)
        }
    }

    fun getPreviewView(): PreviewView {
        return previewView
    }

    fun setCameraManager(manager: CameraManager) {
        this.cameraManager = manager
        // Bind the preview view to the camera manager
        manager.bindPreviewView(previewView)
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Restart layout callback when view is reattached
        setupLayoutHack()
        // Rebind the preview view to camera manager when reattached
        cameraManager?.bindPreviewView(previewView)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Remove Choreographer callback to prevent memory leak
        removeLayoutCallback()
        // Clean up camera when view is detached
        cameraManager?.releaseCamera()
    }

    override fun requestLayout() {
        super.requestLayout()
        post(measureAndLayout)
    }

    private val measureAndLayout = Runnable {
        measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        layout(left, top, right, bottom)
    }
}
