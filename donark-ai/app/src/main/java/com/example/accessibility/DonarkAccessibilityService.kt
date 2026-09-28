package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Android Accessibility Service for DONARK AI.
 * Enables screen awareness, text extraction, search submission, and interaction verification.
 */
class DonarkAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "onServiceConnected invoked.")
        DonarkAccessibilityBridge.onServiceConnected(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        DonarkAccessibilityBridge.onAccessibilityEvent(event)
    }

    override fun onInterrupt() {
        Log.w(TAG, "onInterrupt called.")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy called.")
        DonarkAccessibilityBridge.onServiceDisconnected()
    }

    companion object {
        private const val TAG = "DonarkAccessService"
    }
}
