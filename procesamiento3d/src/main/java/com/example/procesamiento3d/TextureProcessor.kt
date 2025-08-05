
package com.example.procesamiento3d

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream

object TextureProcessor {

    suspend fun procesarYCompararTextura(
        context: Context,
        previewView: PreviewView,
        originalBitmap: Bitmap,
        wallName: String
    ): Pair<Bitmap, String?> {
        // 1. Recortar desde el rectángulo en pantalla
        val croppedBitmap = recortarDesdePreviewView(previewView, originalBitmap)

        // 2. Preprocesar con OpenCV
        val processedBitmap = procesarConOpenCV(croppedBitmap)

        // 3. Guardar imagen procesada
        withContext(Dispatchers.IO) {
            val processedFile = File(context.cacheDir, "${wallName.replace(" ", "_")}_Processed.jpg")
            FileOutputStream(processedFile).use { out ->
                processedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        }

        // 4. Buscar textura más parecida
        val textureName = TextureRecognizer.findMostSimilar(context, processedBitmap)

        // 5. Guardar textura sugerida
        if (textureName != null) {
            withContext(Dispatchers.IO) {
                val textureFinalFile = File(context.cacheDir, "${wallName.replace(" ", "_")}_Albedo.png")
                context.assets.open("textures/$textureName").use { input ->
                    textureFinalFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("TextureProcessor", "🎯 Textura sugerida: $textureName")
                Log.d("TextureProcessor", "📂 Guardada en: ${textureFinalFile.absolutePath}")
            }
        }

        return Pair(processedBitmap, textureName)
    }

    private fun recortarDesdePreviewView(
        previewView: PreviewView,
        bitmap: Bitmap,
        rectWidthRatio: Float = 0.6f,
        rectHeightRatio: Float = 0.4f
    ): Bitmap {
        val previewWidth = previewView.width
        val previewHeight = previewView.height

        val rectWidthPx = previewWidth * rectWidthRatio
        val rectHeightPx = previewHeight * rectHeightRatio
        val leftPx = (previewWidth - rectWidthPx) / 2f
        val topPx = (previewHeight - rectHeightPx) / 2f

        val scaleX = bitmap.width.toFloat() / previewWidth
        val scaleY = bitmap.height.toFloat() / previewHeight

        val cropLeft = (leftPx * scaleX).toInt()
        val cropTop = (topPx * scaleY).toInt()
        val cropWidth = (rectWidthPx * scaleX).toInt()
        val cropHeight = (rectHeightPx * scaleY).toInt()

        return Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight)
    }

    private fun procesarConOpenCV(bitmap: Bitmap): Bitmap {
        val src = Mat()

        // Convertir de Bitmap a Mat
        Utils.bitmapToMat(bitmap, src)

        // Verificar si está vacía
        if (src.empty()) {
            Log.e("OpenCV", "❌ Imagen de entrada está vacía.")
            return bitmap
        }

        val converted = Mat()

        // Asegurar tipo correcto (3 canales)
        when (src.channels()) {
            4 -> Imgproc.cvtColor(src, converted, Imgproc.COLOR_RGBA2RGB)
            1 -> Imgproc.cvtColor(src, converted, Imgproc.COLOR_GRAY2RGB)
            3 -> src.copyTo(converted)
            else -> {
                Log.e("OpenCV", "❌ Formato de imagen no compatible: ${src.channels()} canales")
                return bitmap
            }
        }

        // Aplicar preprocesamiento
        val blurred = Mat()
        Imgproc.GaussianBlur(converted, blurred, Size(5.0, 5.0), 0.0)

        val filtered = Mat()
        Imgproc.bilateralFilter(blurred, filtered, 9, 75.0, 75.0)

        // Convertir de vuelta a Bitmap
        val resultBitmap = createBitmap(filtered.cols(), filtered.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(filtered, resultBitmap)

        return resultBitmap
    }
}
