package com.example.roomify

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
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

class PreviewTextureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val texturePath = intent.getStringExtra("texturePath")
        val processedPath = intent.getStringExtra("processedPath")
        val wallName = intent.getStringExtra("wallName") ?: "Pared"

        setContent {
            MaterialTheme {
                // Carga en background (IO) para no bloquear la UI
                val textureBitmap by produceState<Bitmap?>(initialValue = null, texturePath) {
                    value = texturePath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            Log.d("PreviewTexture", "📂 Textura encontrada en cache")
                            withContext(Dispatchers.IO) { loadScaledBitmap(path) }
                        } else {
                            Log.e("PreviewTexture", "❌ No se encontró textura en $path")
                            null
                        }
                    }
                }

                val processedBitmap by produceState<Bitmap?>(initialValue = null, processedPath) {
                    value = processedPath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            Log.d("PreviewTexture", "🧩 Imagen procesada encontrada")
                            withContext(Dispatchers.IO) { loadScaledBitmap(path) }
                        } else {
                            Log.e("PreviewTexture", "❌ No se encontró imagen procesada en $path")
                            null
                        }
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
                            // Si viene una pared destino (applyToWall), copia los archivos con el nombre de esa pared
                            val applyToWall = intent.getStringExtra("applyToWall")
                            val texturePath = intent.getStringExtra("texturePath")
                            val processedPath = intent.getStringExtra("processedPath")

                            if (!applyToWall.isNullOrBlank() && !texturePath.isNullOrBlank()) {
                                val base = applyToWall.replace(" ", "_")
                                val dstAlbedo = File(cacheDir, "${base}_Albedo.png")
                                File(texturePath).copyTo(dstAlbedo, overwrite = true)

                                processedPath?.let { pp ->
                                    val dstProcessed = File(cacheDir, "${base}_Processed.jpg")
                                    File(pp).copyTo(dstProcessed, overwrite = true)
                                }
                            }

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
                                putExtra("wallName", wallName) // para reabrir cámara en esa pared
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
