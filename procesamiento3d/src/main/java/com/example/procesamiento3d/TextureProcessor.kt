package com.example.procesamiento3d

import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

object ImageProcessor {

    fun procesarImagenes(rutas: List<String>): List<Mat> {
        val procesadas = mutableListOf<Mat>()

        for (ruta in rutas) {
            val original = Imgcodecs.imread(ruta)
            if (!original.empty()) {
                val gris = Mat()
                Imgproc.cvtColor(original, gris, Imgproc.COLOR_BGR2GRAY)

                val canny = Mat()
                Imgproc.Canny(gris, canny, 100.0, 200.0)

                procesadas.add(canny)
            }
        }

        return procesadas
    }
}
