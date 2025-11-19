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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.roomify.storage.TextureAssignmentStore
import com.example.roomify.ui.theme.RoomifyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
            RoomifyTheme {
                TextureChooserScaffold(targetWall = targetWall)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TextureChooserScaffold(targetWall: String) {
        val choices by produceState(initialValue = emptyList<TextureChoice>(), key1 = Unit) {
            value = withContext(Dispatchers.IO) { buildUniqueTextureChoices() }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Reutilizar textura",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { inner ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Selecciona textura para: $targetWall",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (choices.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Aún no hay texturas capturadas.")
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(choices) { choice ->
                                    TextureRowCard(
                                        packLabel = choice.packName,
                                        previewPath = choice.previewPath,
                                        onUse = {
                                            val inferredDir = File(filesDir, "pbrpacks/${choice.packName}")
                                            val packPath = if (inferredDir.isDirectory) inferredDir.absolutePath
                                            else File(choice.previewPath).parent

                                            val data = Intent().apply {
                                                putExtra("albedoPath", choice.previewPath)
                                                putExtra("processedPath", choice.processedPath)
                                                putExtra("sourceWall", choice.sourceWallLabel)
                                                putExtra("packName", choice.packName)
                                                putExtra("packPath", packPath)
                                            }
                                            setResult(Activity.RESULT_OK, data)
                                            finish()
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                setResult(
                                    Activity.RESULT_FIRST_USER,
                                    Intent()
                                        .putExtra("capture_new", true)
                                        .putExtra("targetWall", targetWall)
                                )
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Tomar nueva textura") }
                    }
                }
            }
        }
    }

    private fun buildUniqueTextureChoices(): List<TextureChoice> {
        val packsRoot = File(filesDir, "pbrpacks")
        if (!packsRoot.exists() || !packsRoot.isDirectory) {
            Log.d("TextureChooser", "No existe carpeta de packs: ${packsRoot.absolutePath}")
            return emptyList()
        }

        val packDirs = packsRoot.listFiles { f -> f.isDirectory } ?: emptyArray()
        if (packDirs.isEmpty()) {
            Log.d("TextureChooser", "No hay subcarpetas de packs en: ${packsRoot.absolutePath}")
            return emptyList()
        }

        val result = mutableListOf<TextureChoice>()

        for (dir in packDirs) {
            val previewFile = findPreviewTextureInPack(dir) ?: continue

            result += TextureChoice(
                packName = dir.name,
                previewPath = previewFile.absolutePath,
                processedPath = null,
                sourceWallLabel = dir.name
            )
        }

        return result.sortedBy { it.packName.lowercase() }
    }

    private fun findPreviewTextureInPack(packDir: File): File? {
        if (!packDir.isDirectory) return null

        val files = packDir.listFiles { f ->
            f.isFile && (f.name.endsWith(".png", true) || f.name.endsWith(".jpg", true) || f.name.endsWith(".jpeg", true))
        } ?: return null

        val preferred = listOf("albedo", "basecolor", "base_color", "color", "diffuse")
        preferred.forEach { key ->
            files.firstOrNull { f ->
                f.name.contains(key, ignoreCase = true)
            }?.let { return it }
        }

        return files.firstOrNull()
    }
}

@Composable
private fun TextureRowCard(
    packLabel: String,
    previewPath: String,
    onUse: () -> Unit
) {
    val thumb by produceState<Bitmap?>(initialValue = null, key1 = previewPath) {
        value = loadScaledBitmapSafe(previewPath, maxDimension = 512)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUse() },
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary, // café (como botón principal)
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb!!.asImageBitmap(),
                        contentDescription = packLabel,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("N/A", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    packLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Tocar para usar esta textura",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary   // ☕ Café principal en lugar del gris
                )
            }
        }
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
