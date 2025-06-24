package com.example.procesamiento3d

import org.junit.Test
import java.io.File

class KeypointsToPlyExporter {

    @Test
    fun exportarKeypointsComoPly() {
        val input = File("C:\\Users\\Usuario\\Desktop\\RoomifyPruebas\\keypoints.txt")
        val output = File("C:\\Users\\Usuario\\Desktop\\RoomifyPruebas\\keypoints.ply")

        val puntos = mutableListOf<Triple<Float, Float, Float>>()

        input.forEachLine { line ->
            if (line.trim().isEmpty() || line.startsWith("Imagen") || line == "---") return@forEachLine
            val partes = line.split(",")
            if (partes.size == 2) {
                val x = partes[0].toFloatOrNull()
                val y = partes[1].toFloatOrNull()
                if (x != null && y != null) {
                    puntos.add(Triple(x, y, 0f)) // Z = 0 por ahora
                }
            }
        }

        output.printWriter().use { writer ->
            // Encabezado del archivo .ply
            writer.println("ply")
            writer.println("format ascii 1.0")
            writer.println("element vertex ${puntos.size}")
            writer.println("property float x")
            writer.println("property float y")
            writer.println("property float z")
            writer.println("end_header")

            // Escribir puntos
            puntos.forEach { (x, y, z) ->
                writer.println("$x $y $z")
            }
        }

        println("✅ Archivo PLY generado: ${output.absolutePath}")
    }
}
