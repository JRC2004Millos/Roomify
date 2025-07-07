package com.example.procesamiento3d

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.HitResult
import com.google.gson.Gson
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.utils.colorOf

class MedicionActivity : AppCompatActivity() {

    private lateinit var sceneView: ArSceneView
    private lateinit var finalizarBtn: Button
    private val puntos = mutableListOf<Position>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medicion)

        sceneView = findViewById(R.id.sceneView)
        finalizarBtn = findViewById(R.id.finalizarMedicionBtn)

        // Verificar y solicitar permiso de cámara
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                101
            )
        }

        sceneView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val frame = sceneView.arSession?.update()
                val hits: List<HitResult> = frame?.hitTest(event) ?: emptyList()
                val hitResult = hits.firstOrNull()

                hitResult?.let { result ->
                    val pose = result.hitPose
                    val pos = Position(pose.tx(), pose.ty(), pose.tz())
                    puntos.add(pos)
                    drawSphere(pos)
                }
            }
            true
        }

        finalizarBtn.setOnClickListener {
            mostrarJSON()
        }
    }

    private fun drawSphere(position: Position) {
        val sphereNode = ModelNode().apply {
            this.position = position
            loadModelGlbAsync(
                context = this@MedicionActivity,
                glbFileLocation = "models/sphere.glb"
            )
        }
        sceneView.addChild(sphereNode)
    }

    private fun mostrarJSON() {
        val corners = puntos.mapIndexed { index, pos ->
            mapOf(
                "id" to ('A' + index).toString(),
                "position" to listOf(pos.x, pos.z)
            )
        }

        val walls = mutableListOf<Map<String, Any>>()
        for (i in 0 until corners.size - 1) {
            val from = corners[i]
            val to = corners[i + 1]
            val dx = (to["position"] as List<Float>)[0] - (from["position"] as List<Float>)[0]
            val dz = (to["position"] as List<Float>)[1] - (from["position"] as List<Float>)[1]
            val dist = Math.sqrt((dx * dx + dz * dz).toDouble())
            val dir = when {
                dx > 0 && Math.abs(dx) > Math.abs(dz) -> "east"
                dx < 0 && Math.abs(dx) > Math.abs(dz) -> "west"
                dz > 0 -> "north"
                else -> "south"
            }

            walls.add(
                mapOf(
                    "from" to (from["id"] as String),
                    "to" to (to["id"] as String),
                    "distance" to String.format("%.2f", dist).toDouble(),
                    "direction" to dir
                )
            )
        }

        val json: Map<String, Any> = mapOf(
            "room_dimensions" to mapOf("height" to 2.8, "unit" to "meters"),
            "corners" to corners,
            "walls" to walls,
            "obstacles" to emptyList<Any>(),
            "origin_reference" to "A"
        )

        val jsonStr = Gson().toJson(json)

        AlertDialog.Builder(this)
            .setTitle("JSON Generado")
            .setMessage(jsonStr)
            .setPositiveButton("OK", null)
            .show()
    }
}
