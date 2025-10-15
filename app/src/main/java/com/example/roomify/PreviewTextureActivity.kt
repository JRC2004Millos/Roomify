package com.example.roomify

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.example.roomify.storage.TextureAssignmentStore

class PreviewTextureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Datos del intent y resolución de rutas a mostrar ---
        val wallName = intent.getStringExtra("wallName") ?: "Pared"
        val applyToWall = intent.getStringExtra("applyToWall")
        val target = applyToWall ?: wallName
        val base = target.replace(" ", "_")

        val canonicalAlbedo = File(cacheDir, "${base}_Albedo.png")
        val canonicalProcessed = File(cacheDir, "${base}_Processed.jpg")

        val intentAlbedo = intent.getStringExtra("texturePath")
        val intentProcessed = intent.getStringExtra("processedPath")

        // ✅ Lo que realmente se mostrará en pantalla
        val texturePathToShow: String? = when {
            !intentAlbedo.isNullOrBlank() && File(intentAlbedo).exists() -> intentAlbedo
            canonicalAlbedo.exists() -> canonicalAlbedo.absolutePath
            else -> null
        }
        val processedPathToShow: String? = when {
            !intentProcessed.isNullOrBlank() && File(intentProcessed).exists() -> intentProcessed
            canonicalProcessed.exists() -> canonicalProcessed.absolutePath
            else -> null
        }

        Log.d("Preview/resolve", "target=$target")
        Log.d("Preview/resolve", "show Albedo=${texturePathToShow ?: "null"}")
        Log.d("Preview/resolve", "show Processed=${processedPathToShow ?: "null"}")

        setContent {
            MaterialTheme {
                // Carga en background (IO) para no bloquear la UI
                val textureBitmap by produceState<Bitmap?>(initialValue = null, texturePathToShow) {
                    value = texturePathToShow?.let { path ->
                        val file = File(path)
                        if (file.exists()) withContext(Dispatchers.IO) { loadScaledBitmap(path) } else null
                    }
                }

                val processedBitmap by produceState<Bitmap?>(initialValue = null, processedPathToShow) {
                    value = processedPathToShow?.let { path ->
                        val file = File(path)
                        if (file.exists()) withContext(Dispatchers.IO) { loadScaledBitmap(path) } else null
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Vista de: $wallName",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    processedBitmap?.let {
                        Text("🧩 Imagen procesada:", style = MaterialTheme.typography.bodyMedium)
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Imagen procesada",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    } ?: Text("⚠️ No se encontró la imagen procesada.")

                    textureBitmap?.let {
                        Text("🎨 Textura sugerida:", style = MaterialTheme.typography.bodyMedium)
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Textura sugerida",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    } ?: Text("⚠️ No se encontró la textura sugerida.")

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            val applyTo = intent.getStringExtra("applyToWall") ?: (intent.getStringExtra("wallName") ?: "Pared")
                            val destBase = applyTo.replace(" ", "_")
                            val dstAlbedo = File(cacheDir, "${destBase}_Albedo.png")
                            val dstProcessed = File(cacheDir, "${destBase}_Processed.jpg")

                            // ⚠️ Usa exactamente lo que se muestra
                            val srcAlbedoPath = texturePathToShow ?: intent.getStringExtra("texturePath")
                            val srcAlbedo = srcAlbedoPath?.let { File(it) }

                            if (srcAlbedo == null || !srcAlbedo.exists()) {
                                Toast.makeText(this@PreviewTextureActivity, "No hay textura para confirmar.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (!srcAlbedo.absolutePath.equals(dstAlbedo.absolutePath, ignoreCase = true)) {
                                runCatching { srcAlbedo.copyTo(dstAlbedo, overwrite = true) }
                                    .onFailure { e ->
                                        Log.e("PreviewConfirm", "copy albedo fail: ${e.message}")
                                        Toast.makeText(this@PreviewTextureActivity, "Error copiando textura: ${e.message}", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                            }

                            processedPathToShow?.let { pp ->
                                if (!dstProcessed.absolutePath.equals(pp, true) && File(pp).exists()) {
                                    runCatching { File(pp).copyTo(dstProcessed, overwrite = true) }
                                        .onFailure { e -> Log.e("PreviewConfirm", "copy processed fail: ${e.message}") }
                                }
                            }

                            // ===== Persistencia del JSON (pack + path) ANTES de cerrar =====
                            TextureAssignmentStore.loadJson(this@PreviewTextureActivity)

                            // pack: intent -> store -> nombre de carpeta del albedo mostrado
                            val packFromIntent = intent.getStringExtra("packName")
                            val packFromStore  = TextureAssignmentStore.getPack(applyTo)
                            val fallbackPack = srcAlbedo.nameWithoutExtension.ifBlank { "Local" }
                            val packToPersist  = packFromStore ?: packFromIntent ?: fallbackPack

                            // path: intent -> /files/pbrpacks/<pack> -> carpeta del albedo destino
                            val pathFromIntent = intent.getStringExtra("packPath")
                            val pbrDir = File(filesDir, "pbrpacks/$packToPersist")
                            val storePath = TextureAssignmentStore.getPathForWall(applyTo)
                            val pathToPersist = when {
                                !pathFromIntent.isNullOrBlank() -> pathFromIntent
                                pbrDir.isDirectory -> pbrDir.absolutePath
                                !storePath.isNullOrBlank() -> storePath
                                else -> dstAlbedo.parent
                            }

                            // Equivalentes por nombre base
                            fun canonical(label: String) = label.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").trim()
                            val allWalls = runCatching {
                                com.example.procesamiento3d.RoomDataLoader.loadWallsRuntime(this@PreviewTextureActivity)
                            }.getOrElse {
                                com.example.procesamiento3d.RoomDataLoader.loadWalls(this@PreviewTextureActivity)
                            }
                            val eqWalls = allWalls.map { it.label }
                                .filter { canonical(it) == canonical(applyTo) }
                                .ifEmpty { listOf(applyTo) }

                            eqWalls.forEach { w ->
                                TextureAssignmentStore.put(
                                    wall = w,
                                    pack = packToPersist,
                                    path = pathToPersist
                                )
                            }

                            // Log explícito de a dónde se guarda el JSON
                            val outFile = File(getExternalFilesDir(null)!!, "textures_model.json")
                            Log.d("TextureStore", "(Preview) guardando en: ${outFile.absolutePath}")

                            TextureAssignmentStore.saveJson(this@PreviewTextureActivity)

                            setResult(Activity.RESULT_OK)
                            finish()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("✅ Confirmar textura") }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 🔄 Repetir toma
                    OutlinedButton(
                        onClick = {
                            val result = Intent().apply {
                                putExtra("repeat", true)
                                putExtra("wallName", wallName)
                            }
                            setResult(Activity.RESULT_FIRST_USER, result)
                            finish()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("🔄 Repetir toma de textura") }
                }
            }
        }
    }

    private fun canonical(label: String) =
        label.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").trim()
}

fun loadScaledBitmap(path: String, maxDimension: Int = 1080): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)

    val height = options.outHeight
    val width = options.outWidth
    if (height <= 0 || width <= 0) {
        Log.e("loadScaledBitmap", "❌ Error al obtener dimensiones de $path")
        return null
    }

    var scale = 1
    while (height / scale > maxDimension || width / scale > maxDimension) {
        scale *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = scale }
    return BitmapFactory.decodeFile(path, decodeOptions)?.also {
        Log.d("loadScaledBitmap", "✅ Imagen decodificada correctamente")
    } ?: run {
        Log.e("loadScaledBitmap", "❌ No se pudo decodificar el archivo en $path")
        null
    }
}
