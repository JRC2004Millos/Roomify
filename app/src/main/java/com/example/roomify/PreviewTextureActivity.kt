package com.example.roomify

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
import java.io.File

class PreviewTextureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val texturePath = intent.getStringExtra("texturePath")
        val processedPath = intent.getStringExtra("processedPath")
        val wallName = intent.getStringExtra("wallName")

        setContent {
            MaterialTheme {
                val textureBitmap by remember(texturePath) {
                    mutableStateOf(texturePath?.let {
                        val file = File(it)
                        if (file.exists()) {
                            Log.d("PreviewTexture", "📂 Textura encontrada en cache")
                            loadScaledBitmap(it)
                        } else {
                            Log.e("PreviewTexture", "❌ No se encontró textura en $it")
                            null
                        }
                    })
                }

                val processedBitmap by remember(processedPath) {
                    mutableStateOf(processedPath?.let {
                        val file = File(it)
                        if (file.exists()) {
                            Log.d("PreviewTexture", "🧩 Imagen procesada encontrada")
                            loadScaledBitmap(it)
                        } else {
                            Log.e("PreviewTexture", "❌ No se encontró imagen procesada en $it")
                            null
                        }
                    })
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

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            setResult(RESULT_OK)
                            finish()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tomar nueva textura")
                    }
                }
            }
        }
    }
}

fun loadScaledBitmap(path: String, maxDimension: Int = 1080): Bitmap? {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(path, options)

    val (height, width) = options.outHeight to options.outWidth
    if (height <= 0 || width <= 0) {
        Log.e("loadScaledBitmap", "❌ Error al obtener dimensiones de $path")
        return null
    }

    var scale = 1
    while (height / scale > maxDimension || width / scale > maxDimension) {
        scale *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = scale
    }

    return BitmapFactory.decodeFile(path, decodeOptions)?.also {
        Log.d("loadScaledBitmap", "✅ Imagen decodificada correctamente")
    } ?: run {
        Log.e("loadScaledBitmap", "❌ No se pudo decodificar el archivo en $path")
        null
    }
}