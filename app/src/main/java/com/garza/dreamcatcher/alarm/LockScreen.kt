package com.garza.dreamcatcher.alarm

import android.app.Activity
import android.os.Build
import android.view.WindowManager

/** Lets an activity appear over the lock screen (and optionally wake the display). */
@Suppress("DEPRECATION")
fun Activity.showOverLockScreen(turnScreenOn: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        setShowWhenLocked(true)
        if (turnScreenOn) setTurnScreenOn(true)
    } else {
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        if (turnScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
    }
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}
