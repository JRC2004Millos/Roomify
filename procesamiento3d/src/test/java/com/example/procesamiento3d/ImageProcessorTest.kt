package com.example.procesamiento3d

import org.junit.Test
import org.opencv.core.Mat
import org.opencv.core.MatOfKeyPoint
import org.opencv.features2d.ORB
import org.opencv.imgcodecs.Imgcodecs
import java.io.File

class ImageProcessorTest {

    init {
        try {
            System.setProperty("java.library.path", "C:/OpenCV/your_path_here/")
            System.load("C:\\Users\\Usuario\\Downloads\\opencv\\build\\java\\x64\\opencv_java480.dll")
            println("✅ OpenCV cargado manualmente")
        } catch (e: UnsatisfiedLinkError) {
            println("❌ Error al cargar OpenCV: ${e.message}")
        }
    }

    @Test
    fun generarKeypointsTXT() {
        val rutas = listOf(
            "C:\\Users\\Usuario\\Desktop\\RoomifyPruebas\\img1.jpg",
            "C:\\Users\\Usuario\\Desktop\\RoomifyPruebas\\img2.jpg",
            "C:\\Users\\Usuario\\Desktop\\RoomifyPruebas\\img3.jpg",
            "C:\\Users\\Usuario\\Desktop\\RoomifyPruebas\\img4.jpg"
        )

        val outDir = File("C:\\Users\\Usuario\\Desktop\\RoomifyPruebas")
        if (!outDir.exists()) outDir.mkdirs()

        val outputFile = File(outDir, "keypoints.txt")
        outputFile.printWriter().use { writer ->
            rutas.forEachIndexed { index, ruta ->
                val imagen: Mat = Imgcodecs.imread(ruta)
                if (imagen.empty()) {
                    println("⚠️ Imagen no encontrada: $ruta")
                    return@forEachIndexed
                }

                val orb = ORB.create()
                val keypoints = MatOfKeyPoint()
                orb.detect(imagen, keypoints)

                writer.println("Imagen $index: $ruta")
                keypoints.toArray().forEach { kp ->
                    writer.println("${kp.pt.x},${kp.pt.y}")
                }
                writer.println("---")
                println("✅ ${keypoints.toArray().size} puntos detectados en img$index")
            }
        }

        println("📄 Archivo generado en: ${outputFile.absolutePath}")
    }
}
