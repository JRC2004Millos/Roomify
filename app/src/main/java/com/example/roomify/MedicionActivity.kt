package com.example.roomify

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.roomify.ar.LineOverlayView
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import io.github.sceneview.ar.ArSceneView
import kotlin.math.sqrt
import java.util.Locale
import android.os.Handler
import android.os.Looper
import android.opengl.Matrix
import com.google.ar.core.Camera
import com.google.ar.core.TrackingState
import com.google.ar.core.HitResult

class MedicionActivity : AppCompatActivity() {

    private lateinit var arSceneView: ArSceneView
    private lateinit var tvDistance: TextView
    private lateinit var btnAddPoint: ImageButton
    private lateinit var lineOverlay: LineOverlayView
    private val ui = Handler(Looper.getMainLooper())

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProj = FloatArray(16)
    private val worldPoint = FloatArray(4)
    private val clip = FloatArray(4)

    private var previewRunning = false
    private var firstAnchor: Anchor? = null
    private var secondAnchor: Anchor? = null

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

        btnAddPoint.setOnClickListener { placePointAtCenter() }
    }

    override fun onResume() {
        super.onResume()
        arSceneView.onResume(this)
    }

    override fun onPause() {
        super.onPause()
        arSceneView.onPause(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        arSceneView.destroy()
    }

    private fun placePointAtCenter() {
        val frame = arSceneView.arSession?.update() ?: return
        val vw = arSceneView.width
        val vh = arSceneView.height
        if (vw <= 0 || vh <= 0) return

        val cx = vw / 2f
        val cy = vh / 2f
        val cam = frame.camera

        val hit = hitTestAtCenterRobust(frame, cx, cy) ?: run {
            lineOverlay.previewScreenX = null
            lineOverlay.previewScreenY = null
            lineOverlay.invalidate()
            return
        }

        val pose = hit.hitPose
        worldToScreen(cam, pose.tx(), pose.ty(), pose.tz(), vw, vh)?.let { (sx, sy) ->
            lineOverlay.previewScreenX = sx
            lineOverlay.previewScreenY = sy
        }

        val newAnchor = hit.createAnchor()

        if (firstAnchor == null) {
            firstAnchor = newAnchor
            firstAnchor?.pose?.let { p ->
                worldToScreen(cam, p.tx(), p.ty(), p.tz(), vw, vh)?.let { (sx, sy) ->
                    lineOverlay.firstScreenX = sx
                    lineOverlay.firstScreenY = sy
                    lineOverlay.previewScreenX = null
                    lineOverlay.previewScreenY = null
                    lineOverlay.invalidate()
                }
            }
            startPreviewLoop()

        } else if (secondAnchor == null) {
            secondAnchor = newAnchor
            secondAnchor?.pose?.let { p2 ->
                worldToScreen(cam, p2.tx(), p2.ty(), p2.tz(), vw, vh)?.let { (sx, sy) ->
                    lineOverlay.secondScreenX = sx
                    lineOverlay.secondScreenY = sy
                    lineOverlay.invalidate()
                }
            }
            val d = distanceMeters(firstAnchor!!.pose, secondAnchor!!.pose)
            tvDistance.text = String.format(Locale.US, "%.2f m", d)

        } else {
            clearLastMeasurement()
            firstAnchor = newAnchor
            firstAnchor?.pose?.let { p ->
                worldToScreen(cam, p.tx(), p.ty(), p.tz(), vw, vh)?.let { (sx, sy) ->
                    lineOverlay.firstScreenX = sx
                    lineOverlay.firstScreenY = sy
                    lineOverlay.previewScreenX = null
                    lineOverlay.previewScreenY = null
                    lineOverlay.secondScreenX = null
                    lineOverlay.secondScreenY = null
                    lineOverlay.invalidate()
                }
            }
            tvDistance.text = "0.00 m"
            startPreviewLoop()
        }
    }

    private fun worldToScreen(
        cam: Camera,
        wx: Float, wy: Float, wz: Float,
        vw: Int, vh: Int
    ): Pair<Float, Float>? {
        cam.getProjectionMatrix(proj, 0, 0.01f, 100f)
        cam.getViewMatrix(view, 0)
        Matrix.multiplyMM(viewProj, 0, proj, 0, view, 0)

        worldPoint[0] = wx; worldPoint[1] = wy; worldPoint[2] = wz; worldPoint[3] = 1f
        Matrix.multiplyMV(clip, 0, viewProj, 0, worldPoint, 0)

        val w = clip[3]
        if (w == 0f) return null

        val ndcX = clip[0] / w
        val ndcY = clip[1] / w
        val ndcZ = clip[2] / w
        if (ndcZ < -1f || ndcZ > 1f) return null

        val sx = ((ndcX + 1f) * 0.5f) * vw
        val sy = ((1f - (ndcY + 1f) * 0.5f)) * vh
        return sx to sy
    }

    private fun distanceMeters(p1: Pose, p2: Pose): Double {
        val dx = p1.tx() - p2.tx()
        val dy = p1.ty() - p2.ty()
        val dz = p1.tz() - p2.tz()
        return sqrt((dx*dx + dy*dy + dz*dz).toDouble())
    }

    private fun clearLastMeasurement() {
        firstAnchor?.detach()
        secondAnchor?.detach()
        firstAnchor = null
        secondAnchor = null
        lineOverlay.clearAll()
        stopPreviewLoop()
    }

    private fun startPreviewLoop() {
        if (previewRunning) return
        previewRunning = true
        ui.post(previewTick)
    }

    private fun stopPreviewLoop() {
        previewRunning = false
    }

    private val previewTick = object : Runnable {
        override fun run() {
            if (!previewRunning) return
            val session = arSceneView.arSession
            val vw = arSceneView.width
            val vh = arSceneView.height

            if (session != null && vw > 0 && vh > 0) {
                val frame = session.update()
                val cam = frame.camera

                if (cam.trackingState != TrackingState.TRACKING) {
                    ui.postDelayed(this, 50L)
                    return
                }

                when {
                    firstAnchor != null && secondAnchor == null -> {
                        firstAnchor?.pose?.let { p1 ->
                            worldToScreen(cam, p1.tx(), p1.ty(), p1.tz(), vw, vh)?.let { (sx, sy) ->
                                lineOverlay.firstScreenX = sx
                                lineOverlay.firstScreenY = sy
                            }
                        }

                        val cx = vw / 2f
                        val cy = vh / 2f
                        val hit = hitTestAtCenterRobust(frame, cx, cy)
                        if (hit != null) {
                            val pose = hit.hitPose
                            worldToScreen(cam, pose.tx(), pose.ty(), pose.tz(), vw, vh)?.let { (sx, sy) ->
                                lineOverlay.previewScreenX = sx
                                lineOverlay.previewScreenY = sy
                            }
                        } else {
                            lineOverlay.previewScreenX = null
                            lineOverlay.previewScreenY = null
                        }
                    }

                    firstAnchor != null && secondAnchor != null -> {
                        firstAnchor?.pose?.let { p1 ->
                            worldToScreen(cam, p1.tx(), p1.ty(), p1.tz(), vw, vh)?.let { (sx, sy) ->
                                lineOverlay.firstScreenX = sx
                                lineOverlay.firstScreenY = sy
                            }
                        }
                        secondAnchor?.pose?.let { p2 ->
                            worldToScreen(cam, p2.tx(), p2.ty(), p2.tz(), vw, vh)?.let { (sx, sy) ->
                                lineOverlay.secondScreenX = sx
                                lineOverlay.secondScreenY = sy
                            }
                        }
                        lineOverlay.previewScreenX = null
                        lineOverlay.previewScreenY = null
                    }

                    else -> lineOverlay.clearAll()
                }

                lineOverlay.invalidate()
            }

            ui.postDelayed(this, 50L)
        }
    }

    // --- NUEVA LÓGICA DE HIT TEST ---
    private fun hitTestAtCenterRobust(
        frame: com.google.ar.core.Frame,
        cx: Float,
        cy: Float
    ): HitResult? {
        val results = frame.hitTest(cx, cy)

        var depthHit: HitResult? = null
        var verticalPlaneHit: HitResult? = null
        var horizontalPlaneHit: HitResult? = null
        var pointHit: HitResult? = null

        // Filtra los resultados para priorizar los que tienen un estado de seguimiento confiable.
        val validResults = results.filter { it.trackable.trackingState == TrackingState.TRACKING }

        for (r in validResults) {
            when (val t = r.trackable) {
                is com.google.ar.core.DepthPoint -> {
                    if (depthHit == null) depthHit = r
                }
                is com.google.ar.core.Plane -> {
                    when (t.type) {
                        com.google.ar.core.Plane.Type.VERTICAL -> {
                            // Prioriza el plano vertical si lo encuentras.
                            if (verticalPlaneHit == null) {
                                verticalPlaneHit = r
                            }
                        }
                        com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING,
                        com.google.ar.core.Plane.Type.HORIZONTAL_DOWNWARD_FACING -> {
                            // Prioriza el plano horizontal si lo encuentras y cumple con el polígono.
                            if (t.isPoseInPolygon(r.hitPose) && horizontalPlaneHit == null) {
                                horizontalPlaneHit = r
                            }
                        }
                    }
                }
                is com.google.ar.core.Point -> {
                    if (pointHit == null) {
                        // Filtra puntos que estén muy lejos.
                        if (r.hitPose.translation.size >= 3) {
                            val pose = r.hitPose.translation
                            val distance = sqrt(pose[0] * pose[0] + pose[1] * pose[1] + pose[2] * pose[2])
                            if (distance < 3.0f) { // Considera puntos a menos de 3 metros
                                pointHit = r
                            }
                        }
                    }
                }
            }
        }

        // Orden de preferencia: Depth > VerticalPlane > Point > HorizontalPlane
        // Priorizamos el punto por encima del plano horizontal para capturar techos y paredes lisas.
        return depthHit ?: verticalPlaneHit ?: pointHit ?: horizontalPlaneHit
    }
}