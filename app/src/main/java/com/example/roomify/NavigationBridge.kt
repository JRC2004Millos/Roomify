package com.example.roomify

import android.app.Activity
import android.content.Intent
import android.util.Log

object NavigationBridge {
    @JvmStatic
    fun openCaptureAndFinish(activity: Activity, jsonPath: String?) {
        val i = Intent(activity, CaptureActivity::class.java).apply {
            putExtra("ROOM_JSON_PATH", jsonPath ?: "")
        }
        // limpias el back stack para que no quede la UnityPlayerActivity “debajo”
        i.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        activity.startActivity(i)
        activity.finish() // ← cierra UnityPlayerActivity
        activity.overridePendingTransition(0, 0)
    }
}
