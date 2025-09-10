package com.example.roomify.ar

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class LineOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val planeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x44FFA500.toInt() // naranja translúcido
    }
    private val planeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFFFFA500.toInt()
    }

    private val planePolys: MutableList<List<PointF>> = mutableListOf()

    fun setPlanePolygons(polys: List<List<Pair<Float, Float>>>?) {
        planePolys.clear()
        polys?.forEach { poly ->
            planePolys += poly.map { PointF(it.first, it.second) }
        }
        invalidate()
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = 0xFFFFFFFF.toInt()
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0x80FFFFFF.toInt()
        pathEffect = DashPathEffect(floatArrayOf(16f, 12f), 0f) // opcional: punteada
    }

    private val snapRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFF4CAF50.toInt() // verde
    }

    private val poly = mutableListOf<PointF>()
    private var previewPoint: PointF? = null
    private var closePolygon: Boolean = false

    private var snapX: Float? = null
    private var snapY: Float? = null

    fun setPoints(points: List<Pair<Float, Float>>) {
        poly.clear()
        for ((x, y) in points) poly += PointF(x, y)
        invalidate()
    }

    fun setPreview(p: Pair<Float, Float>?) {
        previewPoint = p?.let { PointF(it.first, it.second) }
        invalidate()
    }

    fun clearAll() {
        poly.clear()
        previewPoint = null
        closePolygon = false
        snapX = null; snapY = null
        invalidate()
    }

    fun setClosed(closed: Boolean) {
        closePolygon = closed
        invalidate()
    }

    fun isClosed(): Boolean = closePolygon

    fun setSnap(p: Pair<Float, Float>?) {
        if (p == null) { snapX = null; snapY = null }
        else { snapX = p.first; snapY = p.second }
        invalidate()
    }

    fun hasSnap(): Boolean = snapX != null && snapY != null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (poly in planePolys) {
            if (poly.size >= 3) {
                val path = Path().apply {
                    moveTo(poly[0].x, poly[0].y)
                    for (i in 1 until poly.size) lineTo(poly[i].x, poly[i].y)
                    close()
                }
                canvas.drawPath(path, planeFill)
                canvas.drawPath(path, planeStroke)
            }
        }

        // Polilínea fija
        if (poly.size >= 2) {
            val path = Path().apply {
                moveTo(poly[0].x, poly[0].y)
                for (i in 1 until poly.size) lineTo(poly[i].x, poly[i].y)
                if (closePolygon) lineTo(poly[0].x, poly[0].y)
            }
            canvas.drawPath(path, linePaint)
        }

        // Línea de preview (último -> preview), sólo si no está cerrado
        if (poly.isNotEmpty() && previewPoint != null && !closePolygon) {
            val last = poly.last()
            canvas.drawLine(last.x, last.y, previewPoint!!.x, previewPoint!!.y, previewPaint)
        }

        // Puntos
        for (p in poly) {
            canvas.drawCircle(p.x, p.y, 8f, pointPaint)
        }
    }
}
