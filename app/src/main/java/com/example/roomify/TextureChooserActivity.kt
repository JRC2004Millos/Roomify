package com.example.roomify

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

data class CapturedTexture(
    val wallLabel: String,
    val albedoPath: String,
    val processedPath: String?
)
class TextureChooserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetWall = intent.getStringExtra("targetWall") ?: "Pared"

        setContent {
            MaterialTheme {
                val textures by remember { mutableStateOf(loadCapturedTextures(cacheDir)) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Selecciona textura para: $targetWall",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(12.dp))

                    if (textures.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Aún no hay texturas capturadas.")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f)
                        ) {
                            items(textures) { item ->
                                TextureRow(
                                    item = item,
                                    onUse = {
                                        val data = Intent().apply {
                                            putExtra("albedoPath", item.albedoPath)
                                            putExtra("processedPath", item.processedPath)
                                            putExtra("sourceWall", item.wallLabel)
                                        }
                                        setResult(Activity.RESULT_OK, data)
                                        finish()
                                    }
                                )
                                Divider()
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            setResult(
                                Activity.RESULT_FIRST_USER,
                                Intent().putExtra("capture_new", true)
                                    .putExtra("targetWall", targetWall)
                            )
                            finish()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("📷 Tomar nueva textura") }
                }
            }
        }
    }
}

@Composable
private fun TextureRow(item: CapturedTexture, onUse: () -> Unit) {
    // Decodifica escalado (p. ej. 512 px máx) para evitar bitmaps gigantes
    val thumb by produceState<Bitmap?>(initialValue = null, item.albedoPath) {
        value = loadScaledBitmapSafe(item.albedoPath, maxDimension = 512)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUse() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb!!.asImageBitmap(),
                contentDescription = item.wallLabel,
                modifier = Modifier
                    .size(72.dp),              // el bitmap ya viene chico
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) { Text("N/A") }
        }

        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.wallLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(File(item.albedoPath).name, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onUse) { Text("Usar") }
    }
}

private fun loadCapturedTextures(cacheDir: File): List<CapturedTexture> {
    val albedos = cacheDir.listFiles { f ->
        f.isFile && f.name.endsWith("_Albedo.png")
    } ?: emptyArray()

    return albedos.map { albedo ->
        val base = albedo.name.removeSuffix("_Albedo.png")
        val processed = File(cacheDir, "${base}_Processed.jpg").takeIf { it.exists() }?.absolutePath
        CapturedTexture(
            wallLabel = base.replace("_", " "),
            albedoPath = albedo.absolutePath,
            processedPath = processed
        )
    }.sortedBy { it.wallLabel.lowercase() }
}

fun loadScaledBitmapSafe(path: String, maxDimension: Int = 1024): Bitmap? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, opts)

    val (srcW, srcH) = opts.outWidth to opts.outHeight
    if (srcW <= 0 || srcH <= 0) {
        Log.e("loadScaledBitmapSafe", "Dimensiones inválidas para $path")
        return null
    }

    // calcula inSampleSize como potencia de 2
    var sample = 1
    while ((srcW / sample) > maxDimension || (srcH / sample) > maxDimension) {
        sample *= 2
    }

    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565 // reduce memoria
    }
    return BitmapFactory.decodeFile(path, decodeOpts)
}

