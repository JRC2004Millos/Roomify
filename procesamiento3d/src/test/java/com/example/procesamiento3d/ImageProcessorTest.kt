package com.example.procesamiento3d

import org.junit.Test
import java.io.File

class ImageProcessorTest {

    @Test
    fun generarHabitacionPLY() {
        // 📏 Dimensiones en metros
        val ancho = 4.0    // X
        val largo = 5.0    // Z
        val alto = 2.8     // Y

        val pasosXY = 10
        val pasosXZ = 10
        val pasosYZ = 10

        val puntos3D = mutableListOf<Triple<Double, Double, Double>>()

        val pasoX = ancho / pasosXY
        val pasoY = alto / pasosYZ
        val pasoZ = largo / pasosXZ

        // Piso (Y = 0)
        for (i in 0..pasosXY) {
            for (j in 0..pasosXZ) {
                val x = i * pasoX
                val z = j * pasoZ
                puntos3D.add(Triple(x, 0.0, z))
            }
        }

        // Techo (Y = alto)
        for (i in 0..pasosXY) {
            for (j in 0..pasosXZ) {
                val x = i * pasoX
                val z = j * pasoZ
                puntos3D.add(Triple(x, alto, z))
            }
        }

        // Pared izquierda (X = 0)
        for (i in 0..pasosYZ) {
            for (j in 0..pasosXZ) {
                val y = i * pasoY
                val z = j * pasoZ
                puntos3D.add(Triple(0.0, y, z))
            }
        }

        // Pared derecha (X = ancho)
        for (i in 0..pasosYZ) {
            for (j in 0..pasosXZ) {
                val y = i * pasoY
                val z = j * pasoZ
                puntos3D.add(Triple(ancho, y, z))
            }
        }

        // Pared frontal (Z = 0)
        for (i in 0..pasosXY) {
            for (j in 0..pasosYZ) {
                val x = i * pasoX
                val y = j * pasoY
                puntos3D.add(Triple(x, y, 0.0))
            }
        }

        // Pared trasera (Z = largo)
        for (i in 0..pasosXY) {
            for (j in 0..pasosYZ) {
                val x = i * pasoX
                val y = j * pasoY
                puntos3D.add(Triple(x, y, largo))
            }
        }

        // 📝 Exportar archivo .PLY
        val outFile = File("C:/Users/Usuario/Desktop/RoomifyPruebas/habitacion_sintetica.ply")
        val writer = outFile.printWriter()
        writer.println("ply")
        writer.println("format ascii 1.0")
        writer.println("element vertex ${puntos3D.size}")
        writer.println("property float x")
        writer.println("property float y")
        writer.println("property float z")
        writer.println("end_header")
        puntos3D.forEach { (x, y, z) ->
            writer.println("$x $y $z")
        }
        writer.close()

        println("✅ Archivo generado con ${puntos3D.size} puntos.")
    }
}
