package com.example.procesamiento3d

import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File

object TextureProcessor {

    fun generarTexturaDesdeRuta(entrada: String, salida: String): Boolean {
        try {
            val imagen = Imgcodecs.imread(entrada)
            if (imagen.empty()) return false

            val gris = Mat()
            Imgproc.cvtColor(imagen, gris, Imgproc.COLOR_BGR2GRAY)

            val ecualizada = Mat()
            Imgproc.equalizeHist(gris, ecualizada)

            val centro = Rect(
                imagen.cols() / 4,
                imagen.rows() / 4,
                imagen.cols() / 2,
                imagen.rows() / 2
            )
            val recorte = Mat(imagen, centro)

            val salidaMat = Mat()
            Imgproc.resize(recorte, salidaMat, Size(512.0, 512.0))

            val archivoSalida = File(salida)
            return Imgcodecs.imwrite(archivoSalida.absolutePath, salidaMat)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
