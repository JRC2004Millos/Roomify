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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.example.roomify.storage.TextureAssignmentStore
import com.example.roomify.ui.theme.RoomifyTheme

class PreviewTextureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wallName = intent.getStringExtra("wallName") ?: "Pared"
        val applyToWall = intent.getStringExtra("applyToWall")
        val target = applyToWall ?: wallName
        val base = target.replace(" ", "_")

        val canonicalAlbedo = File(cacheDir, "${base}_Albedo.png")
        val canonicalProcessed = File(cacheDir, "${base}_Processed.jpg")

        val intentAlbedo = intent.getStringExtra("texturePath")
        val intentProcessed = intent.getStringExtra("processedPath")

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

        setContent {
            RoomifyTheme {
                PreviewScaffold(
                    wallName = wallName,
                    texturePathToShow = texturePathToShow,
                    processedPathToShow = processedPathToShow,
                    onConfirm = { confirmSelection(target, texturePathToShow, processedPathToShow) },
                    onRetake = { retake(wallName) }
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PreviewScaffold(
        wallName: String,
        texturePathToShow: String?,
        processedPathToShow: String?,
        onConfirm: () -> Unit,
        onRetake: () -> Unit
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Vista previa de textura",
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
                    PreviewContent(
                        wallName = wallName,
                        texturePathToShow = texturePathToShow,
                        processedPathToShow = processedPathToShow,
                        onConfirm = onConfirm,
                        onRetake = onRetake
                    )
                }
            }
        }
    }

    @Composable
    private fun PreviewContent(
        wallName: String,
        texturePathToShow: String?,
        processedPathToShow: String?,
        onConfirm: () -> Unit,
        onRetake: () -> Unit
    ) {
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
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Superficie: $wallName",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Imagen procesada", style = MaterialTheme.typography.bodyMedium)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp) // más corta
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (processedBitmap != null) {
                        Image(
                            bitmap = processedBitmap!!.asImageBitmap(),
                            contentDescription = "Imagen procesada",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("No se encontró la imagen procesada.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Textura sugerida", style = MaterialTheme.typography.bodyMedium)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp) // más corta
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (textureBitmap != null) {
                        Image(
                            bitmap = textureBitmap!!.asImageBitmap(),
                            contentDescription = "Textura sugerida",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("No se encontró la textura sugerida.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Confirmar textura") }

                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Repetir toma de textura") }
            }
        }
    }

    private fun confirmSelection(
        applyTo: String,
        texturePathToShow: String?,
        processedPathToShow: String?
    ) {
        val destBase = applyTo.replace(" ", "_")
        val dstAlbedo = File(cacheDir, "${destBase}_Albedo.png")
        val dstProcessed = File(cacheDir, "${destBase}_Processed.jpg")

        val srcAlbedoPath = texturePathToShow ?: intent.getStringExtra("texturePath")
        val srcAlbedo = srcAlbedoPath?.let { File(it) }

        if (srcAlbedo == null || !srcAlbedo.exists()) {
            Toast.makeText(this, "No hay textura para confirmar.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!srcAlbedo.absolutePath.equals(dstAlbedo.absolutePath, ignoreCase = true)) {
            runCatching { srcAlbedo.copyTo(dstAlbedo, overwrite = true) }
                .onFailure { e ->
                    Log.e("PreviewConfirm", "copy albedo fail: ${e.message}")
                    Toast.makeText(this, "Error copiando textura: ${e.message}", Toast.LENGTH_SHORT).show()
                    return
                }
        }

        processedPathToShow?.let { pp ->
            if (!dstProcessed.absolutePath.equals(pp, true) && File(pp).exists()) {
                runCatching { File(pp).copyTo(dstProcessed, overwrite = true) }
                    .onFailure { e -> Log.e("PreviewConfirm", "copy processed fail: ${e.message}") }
            }
        }

        TextureAssignmentStore.loadJson(this)

        val packFromIntent = intent.getStringExtra("packName")
        val packFromStore  = TextureAssignmentStore.getPack(applyTo)
        val fallbackPack = srcAlbedo.nameWithoutExtension.ifBlank { "Local" }
        val packToPersist  = packFromStore ?: packFromIntent ?: fallbackPack

        val pathFromIntent = intent.getStringExtra("packPath")
        val pbrDir = File(filesDir, "pbrpacks/$packToPersist")
        val storePath = TextureAssignmentStore.getPathForWall(applyTo)
        val pathToPersist = when {
            !pathFromIntent.isNullOrBlank() -> pathFromIntent
            pbrDir.isDirectory -> pbrDir.absolutePath
            !storePath.isNullOrBlank() -> storePath
            else -> dstAlbedo.parent
        }

        fun canonical(label: String) = label.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").trim()
        val allWalls = runCatching {
            com.example.procesamiento3d.RoomDataLoader.loadWallsRuntime(this)
        }.getOrElse {
            com.example.procesamiento3d.RoomDataLoader.loadWalls(this)
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

        val outFile = File(getExternalFilesDir(null)!!, "textures_model.json")
        Log.d("TextureStore", "(Preview) guardando en: ${outFile.absolutePath}")

        TextureAssignmentStore.saveJson(this)

        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun retake(wallName: String) {
        val result = Intent().apply {
            putExtra("repeat", true)
            putExtra("wallName", wallName)
        }
        setResult(Activity.RESULT_FIRST_USER, result)
        finish()
    }
}

fun loadScaledBitmap(path: String, maxDimension: Int = 1080): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)

    val height = options.outHeight
    val width = options.outWidth
    if (height <= 0 || width <= 0) {
        Log.e("loadScaledBitmap", "Error al obtener dimensiones de $path")
        return null
    }

    var scale = 1
    while (height / scale > maxDimension || width / scale > maxDimension) {
        scale *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = scale }
    return BitmapFactory.decodeFile(path, decodeOptions)?.also {
        Log.d("loadScaledBitmap", "Imagen decodificada correctamente")
    } ?: run {
        Log.e("loadScaledBitmap", "No se pudo decodificar el archivo en $path")
        null
    }
}
