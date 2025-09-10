package com.example.roomify

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.roomify.ar.LineOverlayView
import com.example.roomify.core.MeasurementController
import com.example.roomify.core.RoomExporter
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ArSceneView
import java.util.Locale

class MedicionActivity : AppCompatActivity() {

    private lateinit var arSceneView: ArSceneView
    private lateinit var tvDistance: TextView
    private lateinit var btnAddPoint: ImageButton
    private lateinit var lineOverlay: LineOverlayView

    private val ui = Handler(Looper.getMainLooper())
    private var previewRunning = false

    private val measure = MeasurementController()
    private val planePolyCache = linkedMapOf<com.google.ar.core.Plane, List<Pair<Float, Float>>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medicion)

        lineOverlay = findViewById(R.id.lineOverlay)
        arSceneView = findViewById(R.id.arSceneView)
        tvDistance = findViewById(R.id.tvDistance)
        btnAddPoint = findViewById(R.id.btnAddPoint)

        arSceneView.configureSession { session, config ->
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            config.depthMode = if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC))
                Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
            config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
            session.configure(config)
        }

        // Malla integrada (visibilidad la controlamos en el tick)
        arSceneView.planeRenderer.isVisible = false

        btnAddPoint.setOnClickListener { placePointAtCenter() }
    }

    override fun onResume() { super.onResume(); arSceneView.onResume(this) }
    override fun onPause() { super.onPause(); arSceneView.onPause(this) }
    override fun onDestroy() { super.onDestroy(); arSceneView.destroy() }

    private fun placePointAtCenter() {
        val frame = arSceneView.arSession?.update() ?: return
        val vw = arSceneView.width; val vh = arSceneView.height
        if (vw <= 0 || vh <= 0) return

        val hit = hitTestAtCenter(frame, vw/2f, vh/2f) ?: return
        measure.placePointFromCenterHit(hit)

        // Distancia del último tramo (UI)
        val n = measure.anchors.size
        tvDistance.text = if (n >= 2) {
            val a = measure.anchors[n-2].pose
            val b = measure.anchors[n-1].pose
            String.format(Locale.US, "%.2f m", measure.distanceMeters(a,b))
        } else "0.00 m"

        startPreviewLoop()
    }

    private fun startPreviewLoop() { if (!previewRunning) { previewRunning = true; ui.post(tick) } }
    private fun stopPreviewLoop() { previewRunning = false }

    private val tick = object: Runnable {
        override fun run() {
            if (!previewRunning) return

            val session = arSceneView.arSession
            val vw = arSceneView.width
            val vh = arSceneView.height

            if (session != null && vw > 0 && vh > 0) {
                // 1) Actualizar frame/cámara UNA sola vez
                val frame = session.update()
                val cam = frame.camera

                // 2) ACTUALIZAR MALLAS PERSISTENTES DE PLANOS (cache)
                // 2.1) Eliminar planos STOPPED o SUBSUMED
                val toRemove = mutableListOf<com.google.ar.core.Plane>()
                for ((pl, _) in planePolyCache) {
                    if (pl.trackingState == TrackingState.STOPPED || pl.subsumedBy != null) {
                        toRemove += pl
                    }
                }
                toRemove.forEach { planePolyCache.remove(it) }

                // 2.2) Recorrer TODOS los planes conocidos por la sesión y proyectarlos a pantalla
                val allPlanes = arSceneView.arSession
                    ?.getAllTrackables(com.google.ar.core.Plane::class.java)
                    .orEmpty()

                for (plane in allPlanes) {
                    if (plane.trackingState == TrackingState.STOPPED || plane.subsumedBy != null) continue

                    val polyBuf = plane.polygon ?: continue
                    val centerPose = plane.centerPose

                    val screenPoly = ArrayList<Pair<Float, Float>>(polyBuf.limit() / 2)
                    polyBuf.rewind()
                    while (polyBuf.hasRemaining()) {
                        val px = polyBuf.get()
                        val pz = polyBuf.get()
                        val local = floatArrayOf(px, 0f, pz)
                        val world = FloatArray(3)
                        centerPose.transformPoint(local, 0, world, 0)
                        measure.projectToScreen(cam, world[0], world[1], world[2], vw, vh)?.let { screenPoly += it }
                    }

                    if (screenPoly.size >= 3) {
                        planePolyCache[plane] = screenPoly
                    }
                }

                // 2.3) Pintar TODAS las mallas persistentes
                lineOverlay.setPlanePolygons(planePolyCache.values.toList())

                // 3) Medición: reproyección + preview + snap (usa el mismo frame)
                val state = measure.tick(frame, vw, vh) {
                    hitTestAtCenter(frame, vw / 2f, vh / 2f)
                }

                // (Si quisieras ocultar la malla integrada según tracking, la dejamos OFF de todos modos)
                arSceneView.planeRenderer.isVisible = false

                // 4) Dibujar overlay de líneas/puntos
                lineOverlay.setPoints(state.screenPoints)
                lineOverlay.setPreview(state.previewPoint)
                lineOverlay.setSnap(state.snapPoint)
                lineOverlay.invalidate()
            }

            ui.postDelayed(this, 50L)
        }
    }

    // Hit test (preferente Depth > Vertical > Point > Horizontal)
    private fun hitTestAtCenter(frame: com.google.ar.core.Frame, cx: Float, cy: Float): HitResult? {
        val results = frame.hitTest(cx, cy)
        var depth: HitResult? = null
        var vert: HitResult? = null
        var horiz: HitResult? = null
        var point: HitResult? = null

        val valid = results.filter { it.trackable.trackingState == TrackingState.TRACKING }
        for (r in valid) when (val t = r.trackable) {
            is com.google.ar.core.DepthPoint -> if (depth == null) depth = r
            is com.google.ar.core.Plane -> when (t.type) {
                com.google.ar.core.Plane.Type.VERTICAL -> if (vert == null) vert = r
                com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING,
                com.google.ar.core.Plane.Type.HORIZONTAL_DOWNWARD_FACING ->
                    if (t.isPoseInPolygon(r.hitPose) && horiz == null) horiz = r
            }
            is com.google.ar.core.Point -> if (point == null) {
                val tr = r.hitPose.translation
                val d = kotlin.math.sqrt(tr[0]*tr[0] + tr[1]*tr[1] + tr[2]*tr[2])
                if (d < 3.0f) point = r
            }
        }
        return depth ?: vert ?: point ?: horiz
    }

    // Botón exportar (android:onClick="onExportClick")
    fun onExportClick(@Suppress("UNUSED_PARAMETER") v: View) {
        if (measure.anchors.size < 3) {
            tvDistance.text = "Necesitas mínimo 3 puntos para exportar."
            return
        }
        // TODO: reemplazar por altura real cuando la midas
        val altura = 2.80f
        val path = RoomExporter.export(this, altura, measure.anchors)
        tvDistance.text = "JSON exportado:\n$path"
    }
}
