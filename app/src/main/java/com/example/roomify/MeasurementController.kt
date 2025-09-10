package com.example.roomify.core

import com.google.ar.core.*
import kotlin.math.sqrt

data class UiState(
    val screenPoints: List<Pair<Float, Float>>,
    val previewPoint: Pair<Float, Float>?,
    val snapPoint: Pair<Float, Float>?,
    val trackingOk: Boolean
)

class MeasurementController(
    private val snapPx: Float = 32f,
    private val snapMeters: Float = 0.10f
) {
    private val snapPx2 = snapPx * snapPx

    // Matrices y buffers para proyección
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProj = FloatArray(16)
    private val worldPoint = FloatArray(4)
    private val clip = FloatArray(4)

    // Estado
    val anchors = mutableListOf<Anchor>()
    private val screenPts = mutableListOf<Pair<Float, Float>>()

    // Checkpoints: tamaños confirmados de anchors (como “commits”)
    private val checkpoints = ArrayDeque<Int>().apply { addLast(0) }

    var snapCandidateIndex = -1
        private set

    // -------- API --------

    fun clear() {
        anchors.toSet().forEach { it.detach() }
        anchors.clear()
        screenPts.clear()
        snapCandidateIndex = -1
        checkpoints.clear()
        checkpoints.addLast(0)
    }

    fun placePointFromCenterHit(hit: HitResult) {
        val anchor = if (snapCandidateIndex >= 0) anchors[snapCandidateIndex] else hit.createAnchor()
        anchors += anchor
    }

    fun confirm() {
        checkpoints.addLast(anchors.size)
    }

    fun undoLast(): Boolean {
        val floor = checkpoints.lastOrNull() ?: 0
        if (anchors.size > floor) {
            val last = anchors.removeLast()
            last.detach()
            return true
        }
        return false
    }

    fun getConfirmedCount(): Int = checkpoints.lastOrNull() ?: 0

    fun distanceMeters(a: Pose, b: Pose): Double {
        val dx = a.tx() - b.tx()
        val dy = a.ty() - b.ty()
        val dz = a.tz() - b.tz()
        return sqrt((dx*dx + dy*dy + dz*dz).toDouble())
    }

    fun tick(
        frame: Frame,
        vw: Int,
        vh: Int,
        hitAtCenter: (() -> HitResult?)
    ): UiState {
        val cam = frame.camera
        if (vw <= 0 || vh <= 0) return UiState(emptyList(), null, null, trackingOk = false)

        // 1) Reproyectar anchors fijos
        val pts = mutableListOf<Pair<Float, Float>>()
        for (anc in anchors) {
            val p = anc.pose
            worldToScreen(cam, p.tx(), p.ty(), p.tz(), vw, vh)?.let { pts += it }
        }
        screenPts.clear()
        screenPts.addAll(pts)

        // 2) Preview + SNAP
        var preview: Pair<Float, Float>? = null
        var snap: Pair<Float, Float>? = null
        snapCandidateIndex = -1

        if (anchors.isNotEmpty() && cam.trackingState == TrackingState.TRACKING) {
            val hit = hitAtCenter()
            if (hit != null) {
                val pose = hit.hitPose
                preview = worldToScreen(cam, pose.tx(), pose.ty(), pose.tz(), vw, vh)

                // Snap al primero por proximidad en px
                if (preview != null && screenPts.isNotEmpty()) {
                    val first = screenPts.first()
                    val d2 = dist2(preview!!, first)
                    if (d2 <= snapPx2) {
                        snap = first
                        preview = first
                    }
                }

                // Snap al punto más cercano (px) + validación en mundo (m)
                if (preview != null && snap == null && screenPts.isNotEmpty()) {
                    val (px, py) = preview!!
                    var bestIdx = -1
                    var bestPxDist = Float.MAX_VALUE
                    screenPts.forEachIndexed { idx, (sx, sy) ->
                        val dx = px - sx
                        val dy = py - sy
                        val d = kotlin.math.sqrt(dx*dx + dy*dy)
                        if (d < bestPxDist) { bestPxDist = d; bestIdx = idx }
                    }

                    val worldOK = anchors.getOrNull(bestIdx)?.let { anc ->
                        val ap = anc.pose
                        val dx = ap.tx() - pose.tx()
                        val dy = ap.ty() - pose.ty()
                        val dz = ap.tz() - pose.tz()
                        val d = kotlin.math.sqrt(dx*dx + dy*dy + dz*dz)
                        d <= snapMeters
                    } ?: false

                    if (bestIdx >= 0 && bestPxDist <= snapPx && worldOK) {
                        snapCandidateIndex = bestIdx
                        snap = screenPts[bestIdx]
                        preview = snap
                    }
                }
            }
        }

        return UiState(
            screenPoints = screenPts.toList(),
            previewPoint = preview,
            snapPoint = snap,
            trackingOk = cam.trackingState == TrackingState.TRACKING
        )
    }

    fun projectToScreen(
        cam: Camera, wx: Float, wy: Float, wz: Float, vw: Int, vh: Int
    ): Pair<Float, Float>? = worldToScreen(cam, wx, wy, wz, vw, vh)

    // -------- helpers internos --------

    private fun worldToScreen(
        cam: Camera, wx: Float, wy: Float, wz: Float, vw: Int, vh: Int
    ): Pair<Float, Float>? {
        cam.getProjectionMatrix(proj, 0, 0.01f, 100f)
        cam.getViewMatrix(view, 0)
        android.opengl.Matrix.multiplyMM(viewProj, 0, proj, 0, view, 0)

        worldPoint[0] = wx; worldPoint[1] = wy; worldPoint[2] = wz; worldPoint[3] = 1f
        android.opengl.Matrix.multiplyMV(clip, 0, viewProj, 0, worldPoint, 0)

        val w = clip[3]; if (w == 0f) return null
        val ndcX = clip[0] / w
        val ndcY = clip[1] / w
        val ndcZ = clip[2] / w
        if (ndcZ < -1f || ndcZ > 1f) return null

        val sx = ((ndcX + 1f) * 0.5f) * vw
        val sy = ((1f - (ndcY + 1f) * 0.5f)) * vh
        return sx to sy
    }

    private fun dist2(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
        val dx = a.first - b.first
        val dy = a.second - b.second
        return dx*dx + dy*dy
    }
}
