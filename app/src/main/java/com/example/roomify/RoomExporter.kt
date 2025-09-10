package com.example.roomify.core

import android.content.Context
import com.example.roomify.model.*
import com.google.ar.core.Anchor
import com.google.gson.GsonBuilder
import kotlin.math.abs
import kotlin.math.sqrt
import java.io.File

object RoomExporter {

    private fun direccionCardinal(fromX: Float, fromZ: Float, toX: Float, toZ: Float): String {
        val dx = toX - fromX
        val dz = toZ - fromZ
        val len = sqrt(dx*dx + dz*dz)
        if (len == 0f) return "east"
        val nx = dx / len
        val nz = dz / len
        return if (abs(nx) > abs(nz)) if (nx > 0) "east" else "west" else if (nz > 0) "north" else "south"
    }

    fun export(context: Context, alturaEnMetros: Float, anchorsEnOrden: List<Anchor>): String {
        require(anchorsEnOrden.size >= 3) { "Se requieren al menos 3 esquinas" }

        // Corners A..Z
        val corners = mutableListOf<Corner>()
        var id = 'A'
        for (a in anchorsEnOrden) {
            val p = a.pose
            corners += Corner(id.toString(), XY(p.tx(), p.tz()))
            id++
        }

        // Walls i->i+1
        val walls = mutableListOf<Wall>()
        for (i in corners.indices) {
            val j = (i + 1) % corners.size
            val A = corners[i].position
            val B = corners[j].position
            val dx = B.x - A.x
            val dz = B.y - A.y
            val dist = sqrt(dx*dx + dz*dz)
            val dir = direccionCardinal(A.x, A.y, B.x, B.y)
            walls += Wall(corners[i].id, corners[j].id, dist, dir)
        }

        val data = RoomData(RoomDimensions(alturaEnMetros), corners, walls, emptyList(), "A")

        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(data)

        val file = File(context.filesDir, "room_data.json")
        file.writeText(json, Charsets.UTF_8)
        return file.absolutePath
    }
}
