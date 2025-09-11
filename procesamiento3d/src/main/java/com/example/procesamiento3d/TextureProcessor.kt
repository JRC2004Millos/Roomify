package com.example.procesamiento3d

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.core.graphics.createBitmap
import com.example.procesamiento3d.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
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
        val textureName = enviarImagenAlServidor(context, processedBitmap)

        // 5. Guardar textura sugerida
        if (textureName != null) {
            try {
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
            } catch (e: Exception) {
                Log.e("TextureProcessor", "❌ No se pudo copiar la textura desde assets: ${e.message}")
                return Pair(processedBitmap, null)
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

        Utils.bitmapToMat(bitmap, src)

        if (src.empty()) {
            Log.e("OpenCV", "❌ Imagen de entrada está vacía.")
            return bitmap
        }

        val converted = Mat()
        when (src.channels()) {
            4 -> Imgproc.cvtColor(src, converted, Imgproc.COLOR_RGBA2RGB)
            1 -> Imgproc.cvtColor(src, converted, Imgproc.COLOR_GRAY2RGB)
            3 -> src.copyTo(converted)
            else -> {
                Log.e("OpenCV", "❌ Formato de imagen no compatible: ${src.channels()} canales")
                return bitmap
            }
        }

        val blurred = Mat()
        Imgproc.GaussianBlur(converted, blurred, Size(5.0, 5.0), 0.0)

        val filtered = Mat()
        Imgproc.bilateralFilter(blurred, filtered, 9, 75.0, 75.0)

        val resultBitmap = createBitmap(filtered.cols(), filtered.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(filtered, resultBitmap)

        return resultBitmap
    }
}

private suspend fun enviarImagenAlServidor(context: Context, bitmap: Bitmap): String? {
    return withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "temp_upload.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

            val response = RetrofitClient.api.uploadImage(body)
            if (response.isSuccessful) {
                val texture = response.body()?.texture
                Log.d("TextureProcessor", "✅ Servidor respondió: $texture")
                return@withContext texture
            } else {
                Log.e("TextureProcessor", "❌ Error del servidor: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("TextureProcessor", "❌ Error al enviar imagen: ${e.message}")
        }
        return@withContext null
    }
}
