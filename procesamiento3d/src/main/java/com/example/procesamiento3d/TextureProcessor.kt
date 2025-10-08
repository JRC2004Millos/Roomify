package com.example.procesamiento3d

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.core.graphics.createBitmap
import com.example.procesamiento3d.api.RetrofitClient
import com.example.procesamiento3d.api.TextureResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.zip.ZipFile
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.io.FileOutputStream
import com.example.procesamiento3d.storage.TextureAssignmentStore

object TextureProcessor {
    private val zipClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .callTimeout(0, TimeUnit.MILLISECONDS) // sin límite total
            .build()
    }

    suspend fun procesarYCompararTextura(
        context: Context,
        previewView: PreviewView,
        originalBitmap: Bitmap,
        wallName: String
    ): Pair<Bitmap, String?> {
        val croppedBitmap = recortarDesdePreviewView(previewView, originalBitmap)
        val processedBitmap = procesarConOpenCV(croppedBitmap)

        // Guarda la imagen procesada (tu flujo actual)
        withContext(Dispatchers.IO) {
            val processedFile = File(context.cacheDir, "${wallName.replace(" ", "_")}_Processed.jpg")
            FileOutputStream(processedFile).use { out ->
                processedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        }

        // 1) Enviar imagen → obtener pack y pack_url
        val resp = enviarImagenAlServidor(context, processedBitmap)
        val packUrl = resp?.pack_url
        val packName = resp?.pack ?: "pack"

        // 2) Descargar ZIP y extraer SOLO la imagen de preview
        if (!packUrl.isNullOrBlank()) {
            val zip = descargarZip(context, packUrl, packName)
            if (zip != null) {
                val previewFile = extraerPreviewDelZip(context, zip, resp?.preview)
                if (previewFile != null) {
                    // 3) Guardar la preview donde CaptureActivity ya la espera
                    val dest = File(context.cacheDir, "${wallName.replace(" ", "_")}_Albedo.png")
                    withContext(Dispatchers.IO) {
                        previewFile.inputStream().use { input ->
                            dest.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    Log.d("TextureProcessor", "✅ Preview guardada en: ${dest.absolutePath}")
                } else {
                    Log.e("TextureProcessor", "❌ No se pudo extraer imagen de preview del ZIP")
                }
            } else {
                Log.e("TextureProcessor", "❌ No se pudo descargar el ZIP")
            }
        } else {
            Log.e("TextureProcessor", "❌ pack_url vacío en la respuesta")
        }

        if (resp?.pack != null) {
            TextureAssignmentStore.put(
                wall = wallName,          // nombre de la pared/piso/techo
                pack = resp.pack,         // p.ej. "Wood085A_8K-PNG"
                zip  = resp.pack_url      // URL del .zip (útil para Unity luego)
            )
            TextureAssignmentStore.saveJson(context)  // escribe Android/data/<pkg>/files/example.json
        }
        return Pair(processedBitmap, resp?.pack)
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

    // --- RED: subir imagen y recibir pack_url ---
    private suspend fun enviarImagenAlServidor(
        context: Context,
        bitmap: Bitmap
    ): TextureResponse? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "temp_upload.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

            val response = RetrofitClient.api.uploadImage(body)
            if (response.isSuccessful) {
                response.body()
            } else {
                Log.e("TextureProcessor", "❌ Respuesta HTTP ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e("TextureProcessor", "❌ Error al enviar imagen: ${e.message}")
            null
        }
    }

    private suspend fun descargarZip(
        context: Context,
        packUrl: String,
        packName: String
    ): File? = withContext(Dispatchers.IO) {
        try {
            val destDir = File(context.filesDir, "pbrpacks").apply { mkdirs() }
            val dest = File(destDir, "$packName.zip")

            val req = Request.Builder().url(packUrl).build()
            zipClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e("TextureDL", "❌ HTTP ${resp.code} al bajar ZIP")
                    return@withContext null
                }
                resp.body?.byteStream()?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
            Log.d("TextureDL", "✅ ZIP descargado: ${dest.absolutePath}")
            dest
        } catch (e: Exception) {
            Log.e("TextureDL", "❌ Descarga ZIP falló: ${e.message}")
            null
        }
    }

    private suspend fun extraerPreviewDelZip(
        context: Context,
        zipFile: File,
        previewNameHint: String?
    ): File? = withContext(Dispatchers.IO) {
        val outDir = File(
            context.filesDir,
            "pbrpacks/${zipFile.nameWithoutExtension}"
        ).apply { mkdirs() }

        ZipFile(zipFile).use { zip ->
            // 1) intenta por nombre exacto que vino del server
            var entry: ZipEntry? = null
            if (!previewNameHint.isNullOrBlank()) {
                entry = zip.getEntry(previewNameHint)
            }

            // 2) si no estaba, recorre todas las entradas con sintaxis Kotlin
            if (entry == null) {
                val candidates = sequence {
                    val entries = zip.entries()            // Enumeration<ZipEntry>
                    while (entries.hasMoreElements()) {
                        yield(entries.nextElement())
                    }
                }.filter { it != null && !it.isDirectory &&
                        (it.name.endsWith(".png", true) || it.name.endsWith(".jpg", true))
                }

                entry = candidates.firstOrNull { it.name.contains("_Color", true) }
                    ?: candidates.firstOrNull { it.name.contains("Albedo", true) }
                            ?: candidates.firstOrNull()
            }

            if (entry == null) return@withContext null

            val outFile = File(outDir, File(entry.name).name)
            zip.getInputStream(entry).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            return@withContext outFile
        }
    }
}
