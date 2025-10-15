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
import com.example.roomify.storage.TextureAssignmentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Ítem único por textura (pack). Guardamos una preview y una pared fuente (solo para retorno).
data class TextureChoice(
    val packName: String,
    val previewPath: String,
    val processedPath: String?,
    val sourceWallLabel: String
)

class TextureChooserActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TextureAssignmentStore.loadJson(this)

        val targetWall = intent.getStringExtra("targetWall") ?: "Pared"

        setContent {
            MaterialTheme {
                val choices by produceState(initialValue = emptyList<TextureChoice>(), key1 = Unit) {
                    value = withContext(Dispatchers.IO) { buildUniqueTextureChoices(cacheDir) }
                }

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

                    if (choices.isEmpty()) {
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
                            items(choices) { choice ->
                                TextureRowCompact(
                                    packLabel = choice.packName,
                                    previewPath = choice.previewPath,
                                    onUse = {
                                        // calcula packPath preferido
                                        val inferredDir = File(filesDir, "pbrpacks/${choice.packName}")
                                        val packPath = if (inferredDir.isDirectory) inferredDir.absolutePath
                                        else File(choice.previewPath).parent  // fallback razonable

                                        val data = Intent().apply {
                                            putExtra("albedoPath", choice.previewPath)
                                            putExtra("processedPath", choice.processedPath)
                                            putExtra("sourceWall", choice.sourceWallLabel)
                                            putExtra("packName", choice.packName)   // 👈 nuevo
                                            putExtra("packPath", packPath)          // 👈 nuevo
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

    /**
     * Crea lista de texturas únicas:
     * - Grupo por 'pack' del TextureAssignmentStore
     * - Usa el primer *_Albedo.png encontrado como preview representativa
     * - Mantiene processed si existe para esa misma fuente
     */
    private fun buildUniqueTextureChoices(cacheDir: File): List<TextureChoice> {
        val albedos = cacheDir.listFiles { f ->
            f.isFile && f.name.endsWith("_Albedo.png")
        } ?: emptyArray()

        val uniqueByPack = LinkedHashMap<String, TextureChoice>() // pack -> choice

        for (albedo in albedos) {
            val base = albedo.name.removeSuffix("_Albedo.png")
            val sourceWall = base.replace("_", " ")
            val pack = TextureAssignmentStore.getPack(sourceWall)

            // Si no hay pack, lo tratamos como único (no se puede deduplicar sin pack)
            val packKey = pack ?: "UNASSIGNED::$base"

            if (!uniqueByPack.containsKey(packKey)) {
                val processed = File(cacheDir, "${base}_Processed.jpg")
                    .takeIf { it.exists() }?.absolutePath

                uniqueByPack[packKey] = TextureChoice(
                    packName = pack ?: "Sin nombre",
                    previewPath = albedo.absolutePath,
                    processedPath = processed,
                    sourceWallLabel = sourceWall // solo para retornar; NO se muestra
                )
            }
        }

        // Orden estable por nombre de pack
        return uniqueByPack.values.toList().sortedBy { it.packName.lowercase() }
    }
}

@Composable
private fun TextureRowCompact(
    packLabel: String,
    previewPath: String,
    onUse: () -> Unit
) {
    val thumb by produceState<Bitmap?>(initialValue = null, key1 = previewPath) {
        value = loadScaledBitmapSafe(previewPath, maxDimension = 512)
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
                contentDescription = packLabel,
                modifier = Modifier.size(72.dp),
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
        // 👇 Solo mostramos el nombre del pack (sin pared ni nombre de archivo)
        Text(packLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

private fun loadScaledBitmapSafe(path: String, maxDimension: Int = 1024): Bitmap? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, opts)

    val (srcW, srcH) = opts.outWidth to opts.outHeight
    if (srcW <= 0 || srcH <= 0) {
        Log.e("loadScaledBitmapSafe", "Dimensiones inválidas para $path")
        return null
    }

    var sample = 1
    while ((srcW / sample) > maxDimension || (srcH / sample) > maxDimension) sample *= 2

    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeFile(path, decodeOpts)
}
