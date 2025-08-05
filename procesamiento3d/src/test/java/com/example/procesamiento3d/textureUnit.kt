package com.example.procesamiento3d

import org.junit.Test
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File

class TextureUnit {

    init {
        // Cargar OpenCV manualmente desde su ruta real
        try {
            System.load("")
            println("✅ OpenCV cargado correctamente")
        } catch (e: UnsatisfiedLinkError) {
            println("❌ Error cargando OpenCV: ${e.message}")
        }
    }

    @Test
    fun procesarFotosGeneraTexturas() {
        val rutaEntrada = "C:/Users/Usuario/Desktop/RoomifyPruebas/Sala Juanda"
        val rutaSalida = "C:/Users/Usuario/Desktop/RoomifyPruebas/"

        val carpeta = File(rutaEntrada)
        val archivos = carpeta.listFiles { file ->
            file.extension.lowercase() == "jpg" || file.extension.lowercase() == "png"
        } ?: return

        for (archivo in archivos) {
            val imagen = Imgcodecs.imread(archivo.absolutePath)

            if (imagen.empty()) {
                println("⚠️ No se pudo leer la imagen: ${archivo.absolutePath}")
                continue
            }

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

            val salida = Mat()
            Imgproc.resize(recorte, salida, Size(512.0, 512.0))

            val nombreSalida = File(rutaSalida, "textura_${archivo.nameWithoutExtension}.png")
            Imgcodecs.imwrite(nombreSalida.absolutePath, salida)

            println("✅ Textura generada: ${nombreSalida.absolutePath}")
        }
    }
}
