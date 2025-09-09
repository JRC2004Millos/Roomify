package com.example.roomify.ar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class LineOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paintPreview = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 6f
        style = Paint.Style.STROKE
        color = android.graphics.Color.WHITE
    }

    private val paintFinal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 8f
        style = Paint.Style.STROKE
        color = android.graphics.Color.GREEN
    }

    // Coordenadas proyectadas a pantalla (en píxeles)
    var firstScreenX: Float? = null
    var firstScreenY: Float? = null
    var previewScreenX: Float? = null
    var previewScreenY: Float? = null
    var secondScreenX: Float? = null
    var secondScreenY: Float? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val fx = firstScreenX
        val fy = firstScreenY

        // Línea definitiva
        val sx = secondScreenX
        val sy = secondScreenY
        if (fx != null && fy != null && sx != null && sy != null) {
            canvas.drawLine(fx, fy, sx, sy, paintFinal)
            return
        }

        // Línea preview
        val px = previewScreenX
        val py = previewScreenY
        if (fx != null && fy != null && px != null && py != null) {
            canvas.drawLine(fx, fy, px, py, paintPreview)
        }
    }

    fun clearAll() {
        firstScreenX = null; firstScreenY = null
        previewScreenX = null; previewScreenY = null
        secondScreenX = null; secondScreenY = null
        invalidate()
    }
}
