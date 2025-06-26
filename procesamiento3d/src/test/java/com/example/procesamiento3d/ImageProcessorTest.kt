package com.example.procesamiento3d

import org.junit.Test
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.SIFT
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfPoint2f
import java.io.File

class ImageProcessorTest {
    init {
        try {
            System.load("C:\\Users\\Usuario\\Downloads\\opencv\\build\\java\\x64\\opencv_java480.dll")
            println("✅ OpenCV cargado manualmente")
        } catch (e: UnsatisfiedLinkError) {
            println("❌ Error al cargar OpenCV: " + e.message)
        }
    }

    @Test
    fun generarPLYdesdeMultiplesTriangulaciones() {
        val rutas = listOf(
            "C:\\Users\\Usuario\\Desktop\\RoomifyPruebas\\imga1.jpg",
            "C:\\Users\\Usuario\\Desktop\\RoomifyPruebas\\imga2.jpg",
            "C:\\Users\\Usuario\\Desktop\\RoomifyPruebas\\imga3.jpg",
            "C:\\Users\\Usuario\\Desktop\\RoomifyPruebas\\imga4.jpg"
        )

        val puntos3D = mutableListOf<Triple<Double, Double, Double>>()

        for (i in 0 until rutas.size - 1) {
            val img1 = Imgcodecs.imread(rutas[i], Imgcodecs.IMREAD_GRAYSCALE)
            val img2 = Imgcodecs.imread(rutas[i + 1], Imgcodecs.IMREAD_GRAYSCALE)

            if (img1.empty() || img2.empty()) continue

            val sift = SIFT.create()
            val kp1 = MatOfKeyPoint()
            val kp2 = MatOfKeyPoint()
            val des1 = Mat()
            val des2 = Mat()
            sift.detectAndCompute(img1, Mat(), kp1, des1)
            sift.detectAndCompute(img2, Mat(), kp2, des2)

            val bf = BFMatcher.create()
            val matches = mutableListOf<MatOfDMatch>()
            bf.knnMatch(des1, des2, matches, 2)

            val pts1 = mutableListOf<Point>()
            val pts2 = mutableListOf<Point>()

            for (m in matches) {
                val d = m.toArray()
                if (d.size >= 2 && d[0].distance < 0.75 * d[1].distance) {
                    pts1.add(kp1.toArray()[d[0].queryIdx].pt)
                    pts2.add(kp2.toArray()[d[0].trainIdx].pt)
                }
            }

            val matPts1 = MatOfPoint2f(*pts1.toTypedArray())
            val matPts2 = MatOfPoint2f(*pts2.toTypedArray())
            val fundamental = Calib3d.findFundamentalMat(matPts1, matPts2, Calib3d.FM_RANSAC)

            val focal = 800.0
            val cx = img1.cols() / 2.0
            val cy = img1.rows() / 2.0
            val K = Mat.eye(3, 3, CvType.CV_64F)
            K.put(0, 0, focal)
            K.put(0, 2, cx)
            K.put(1, 1, focal)
            K.put(1, 2, cy)

            val temp = Mat()
            Core.gemm(K.t(), fundamental, 1.0, Mat(), 0.0, temp)
            val E = Mat()
            Core.gemm(temp, K, 1.0, Mat(), 0.0, E)
            val R = Mat()
            val t = Mat()
            Calib3d.recoverPose(E, matPts1, matPts2, K, R, t)

            val P1 = Mat.eye(3, 4, CvType.CV_64F)
            val Rt = Mat(3, 4, CvType.CV_64F)
            R.copyTo(Rt.colRange(0, 3))
            t.copyTo(Rt.col(3))
            val P2 = Mat()
            Core.gemm(K, Rt, 1.0, Mat(), 0.0, P2)

            val pts4D = Mat()
            Calib3d.triangulatePoints(P1, P2, matPts1, matPts2, pts4D)

            for (j in 0 until pts4D.cols()) {
                val x = pts4D[0, j][0] / pts4D[3, j][0]
                val y = pts4D[1, j][0] / pts4D[3, j][0]
                val z = pts4D[2, j][0] / pts4D[3, j][0]
                puntos3D.add(Triple(x, y, z))
            }
        }

        val outFile = File("C:\\Users\\Usuario\\Desktop\\RoomifyPruebas\\keypoints.ply")
        val writer = outFile.printWriter()
        writer.println("ply")
        writer.println("format ascii 1.0")
        writer.println("element vertex ${puntos3D.size}")
        writer.println("property float x")
        writer.println("property float y")
        writer.println("property float z")
        writer.println("end_header")
        puntos3D.forEach { (x, y, z) -> writer.println("$x $y $z") }
        writer.close()
        println("📄 Archivo .ply combinado generado con ${puntos3D.size} puntos.")
    }
}
