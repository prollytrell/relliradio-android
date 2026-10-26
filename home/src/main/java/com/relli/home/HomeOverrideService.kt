package com.relli.home

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.relli.home.ui.MainActivity

/**
 * Google TV won't let you pick a different home screen in Settings. So when the stock Google TV
 * home appears (you pressed Home), this brings RelliHome to the front instead. It's only told about
 * the stock home screen (see res/xml/home_override.xml) and never reads what's on screen.
 */
class HomeOverrideService : AccessibilityService() {
    private var last = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!HomeApp.prefs.takeOverHome.value) return
        val now = SystemClock.elapsedRealtime()
        if (now - last < 600) return
        last = now
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    override fun onInterrupt() {}
}
