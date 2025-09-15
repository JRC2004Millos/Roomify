package com.example.roomify

import android.app.Activity
import android.content.Intent
import android.util.Log

object NavigationBridge {
    /**
     * Abre CaptureActivity y cierra la Activity actual (Unity).
     * Llamado desde Unity cuando termina la medición.
     */
    @JvmStatic
    fun openCaptureAndFinish(activity: Activity, jsonPath: String?) {
        val i = Intent(activity, CaptureActivity::class.java).apply {
            // Opcional: pásale a CaptureActivity la ruta del JSON por si quieres leerla directo
            putExtra("ROOM_JSON_PATH", jsonPath ?: "")
        }
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(i)
        Log.d("NavigationBridge", ">>> Recibido desde Unity con jsonPath=$jsonPath")
    }
}