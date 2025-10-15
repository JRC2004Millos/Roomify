package com.example.roomify

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.procesamiento3d.TextureProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.lifecycle.lifecycleScope
import android.util.Log
import com.example.roomify.storage.TextureAssignmentStore

class CaptureActivity : ComponentActivity() {

    private lateinit var imageCapture: ImageCapture

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.loadLibrary("opencv_java4")

        setContent {
            var selectedWall by remember { mutableStateOf<String?>(null) }
            val context = LocalContext.current
            var pendingTargetWall by remember { mutableStateOf<String?>(null) }
            var refreshKey by remember { mutableStateOf(0) }

            // === 1) PREVIEW launcher ===
            val previewLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                when (result.resultCode) {
                    Activity.RESULT_OK -> {
                        // Confirmó textura → volver a la lista
                        selectedWall = null
                        refreshKey++
                    }
                    Activity.RESULT_FIRST_USER -> {
                        // Repetir toma
                        if (result.data?.getBooleanExtra("repeat", false) == true) {
                            val wall = result.data?.getStringExtra("wallName")
                            if (!wall.isNullOrBlank()) selectedWall = wall
                        }
                    }
                }
            }

            // === 2) PREVIEW lambda ===
            val openPreview: (String?, String?, String, String?, String?) -> Unit =
                { texturePath, processedPath, wallName, packName, packPath ->
                    val intent = Intent(context, PreviewTextureActivity::class.java).apply {
                        putExtra("texturePath", texturePath)
                        putExtra("processedPath", processedPath)
                        putExtra("wallName", wallName)
                        if (!packName.isNullOrBlank()) putExtra("packName", packName)
                        if (!packPath.isNullOrBlank()) putExtra("packPath", packPath)
                    }
                    previewLauncher.launch(intent)
                }

            val chooserLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                when (result.resultCode) {
                    Activity.RESULT_OK -> {
                        val albedo = result.data?.getStringExtra("albedoPath")
                        val processed = result.data?.getStringExtra("processedPath")
                        val sourceWall = result.data?.getStringExtra("sourceWall") ?: "Textura"
                        val packName = result.data?.getStringExtra("packName")   // 👈 nuevo
                        val packPath = result.data?.getStringExtra("packPath")   // 👈 nuevo

                        val applyTo = pendingTargetWall ?: sourceWall

                        if (albedo != null) {
                            val intent = Intent(context, PreviewTextureActivity::class.java).apply {
                                putExtra("texturePath", albedo)
                                putExtra("processedPath", processed)
                                putExtra("wallName", sourceWall)
                                putExtra("applyToWall", applyTo)
                                if (!packName.isNullOrBlank()) putExtra("packName", packName)   // 👈
                                if (!packPath.isNullOrBlank()) putExtra("packPath", packPath)   // 👈
                            }
                            previewLauncher.launch(intent)
                        }
                    }
                    Activity.RESULT_FIRST_USER -> {
                        val wall = result.data?.getStringExtra("targetWall")
                        if (!wall.isNullOrBlank()) selectedWall = wall
                    }
                }
            }

            val openChooser: (String) -> Unit = { targetWall ->
                pendingTargetWall = targetWall
                val intent = Intent(context, TextureChooserActivity::class.java).apply {
                    putExtra("targetWall", targetWall)
                }
                chooserLauncher.launch(intent)
            }

            // === 5) UI principal ===
            if (selectedWall == null) {
                WallListScreen(
                    onWallSelected = { wallName -> selectedWall = wallName },
                    openPreview = openPreview,
                    openChooser = openChooser,
                    refreshKey = refreshKey              // 👈 pásalo a la lista
                )
            } else {
                CameraTextureCapture(
                    wallName = selectedWall!!,
                    openPreview = openPreview
                )
            }
        }
    }

    @Composable
    fun WallListScreen(
        onWallSelected: (String) -> Unit,
        openPreview: (String?, String?, String, String?, String?) -> Unit,
        openChooser: (String) -> Unit,
        refreshKey: Int
    ) {
        val context = LocalContext.current

        // 1) Carga inicial desde tu JSON
        var walls by remember { mutableStateOf(com.example.procesamiento3d.RoomDataLoader.loadWalls(context)) }

        // 2) Observa cambios del JSON en runtime (usa tu observer si lo tienes)
        DisposableEffect(Unit) {
            val observer = com.example.procesamiento3d.RoomJsonObserver(context) {
                walls = runCatching { com.example.procesamiento3d.RoomDataLoader.loadWallsRuntime(context) }
                    .getOrElse { emptyList() }
            }
            observer.startWatching()
            onDispose { observer.stopWatching() }
        }

        LaunchedEffect(refreshKey) {
            TextureAssignmentStore.loadJson(context)
        }

        // 3) Lista
        if (walls.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No hay paredes aún. Ejecuta la medición en Unity para generar room_data.json.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                items(walls) { wall: com.example.procesamiento3d.WallInfo ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                // mantener store al día
                                TextureAssignmentStore.loadJson(context)

                                fun canonical(name: String) =
                                    name.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").trim()

                                val safe = wall.label.replace(" ", "_")
                                val thisAlbedo = File(context.cacheDir, "${safe}_Albedo.png")

                                // 1) si ya está el archivo canónico -> preview directo
                                if (thisAlbedo.exists()) {
                                    openPreview(
                                        thisAlbedo.absolutePath,
                                        File(context.cacheDir, "${safe}_Processed.jpg").absolutePath,
                                        wall.label,
                                        null,   // packName (si quieres: TextureAssignmentStore.getPack(wall.label))
                                        null    // packPath  (si quieres: TextureAssignmentStore.getPathForWall(wall.label))
                                    )
                                    return@clickable
                                }

                                // 2) si el JSON dice que esta pared (o equivalente) YA tiene pack, reconstruir y abrir preview
                                val allWalls = runCatching {
                                    com.example.procesamiento3d.RoomDataLoader.loadWallsRuntime(context)
                                }.getOrElse {
                                    com.example.procesamiento3d.RoomDataLoader.loadWalls(context)
                                }
                                val baseCanon = canonical(wall.label)

                                val packForThis = TextureAssignmentStore.getPack(wall.label)
                                    ?: allWalls.firstOrNull { canonical(it.label) == baseCanon }
                                        ?.let { TextureAssignmentStore.getPack(it.label) }

                                if (packForThis != null) {
                                    val candidate = (context.cacheDir.listFiles { f ->
                                        f.isFile && f.name.endsWith("_Albedo.png")
                                    } ?: emptyArray()).firstOrNull { f ->
                                        val srcWall = f.name.removeSuffix("_Albedo.png").replace("_", " ")
                                        TextureAssignmentStore.getPack(srcWall) == packForThis
                                    }

                                    if (candidate != null) {
                                        try {
                                            candidate.copyTo(thisAlbedo, overwrite = true)
                                            val srcProcessed = File(
                                                context.cacheDir,
                                                "${candidate.name.removeSuffix("_Albedo.png")}_Processed.jpg"
                                            )
                                            val thisProcessed = File(context.cacheDir, "${safe}_Processed.jpg")
                                            if (srcProcessed.exists()) srcProcessed.copyTo(thisProcessed, overwrite = true)

                                            openPreview(
                                                thisAlbedo.absolutePath,
                                                thisProcessed.takeIf { it.exists() }?.absolutePath,
                                                wall.label,
                                                null,   // packName (si quieres: TextureAssignmentStore.getPack(wall.label))
                                                null    // packPath  (si quieres: TextureAssignmentStore.getPathForWall(wall.label))
                                            )
                                            return@clickable
                                        } catch (_: Exception) {
                                            // sigue al fallback
                                        }
                                    }
                                }

                                // 3) fallback: si hay alguna textura en cache -> chooser; si no, cámara
                                val hasAnyTexture = (context.cacheDir.listFiles { f ->
                                    f.isFile && f.name.endsWith("_Albedo.png")
                                } ?: emptyArray()).isNotEmpty()

                                if (hasAnyTexture) {
                                    openChooser(wall.label)
                                } else {
                                    onWallSelected(wall.label)  // cámara
                                }
                            }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = wall.label,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))

                            // Hint visual de estado
                            fun canonical(n: String) = n.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").trim()

                            val safe = wall.label.replace(" ", "_")
                            val hasFile = File(context.cacheDir, "${safe}_Albedo.png").exists()

                            val packDirect = TextureAssignmentStore.getPack(wall.label)
                            val packCanonical = walls.firstOrNull { canonical(it.label) == canonical(wall.label) }
                                ?.let { TextureAssignmentStore.getPack(it.label) }

                            val hasAssigned = (packDirect != null || packCanonical != null)

                            val hasThis = hasFile || hasAssigned

                            val estado = if (hasThis) "Textura capturada" else "Sin textura"
                            Text(
                                text = estado,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasThis) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun CameraTextureCapture(
        wallName: String,
        openPreview: (String?, String?, String, String?, String?) -> Unit
    ) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val previewView = remember { PreviewView(context) }

        var hasPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

        var isProcessing by remember { mutableStateOf(false) }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted -> hasPermission = granted }
        )

        LaunchedEffect(Unit) {
            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        if (hasPermission) {
            val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

            LaunchedEffect(true) {
                kotlinx.coroutines.delay(750) // espera ~0.7s para que Unity libere la cámara
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder().build()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageCapture
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

                // Marco guía
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val rectWidth = size.width * 0.6f
                    val rectHeight = size.height * 0.4f
                    val left = (size.width - rectWidth) / 2f
                    val top = (size.height - rectHeight) / 2f
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(rectWidth, rectHeight),
                        style = Stroke(width = 4f)
                    )
                }

                // Botón capturar
                Button(
                    onClick = {
                        val fileNameBase = wallName.replace(" ", "_")
                        val photoFile = File(context.cacheDir, "$fileNameBase.jpg")
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                        imageCapture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onError(exc: ImageCaptureException) {
                                    Toast.makeText(context, "Error: ${exc.message}", Toast.LENGTH_SHORT).show()
                                }

                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    // Lanzamos en Main para tocar estado, y calculamos en IO
                                    val scope = lifecycleOwner.lifecycleScope
                                    scope.launch(Dispatchers.Main) {
                                        isProcessing = true
                                        val textureName = withContext(Dispatchers.IO) {
                                            val originalBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                            val (_, tName) = TextureProcessor.procesarYCompararTextura(
                                                context = context,
                                                previewView = previewView,
                                                originalBitmap = originalBitmap,
                                                wallName = wallName
                                            )
                                            Log.d("CaptureActivity", "🎯 Resultado de textura sugerida: $tName")
                                            tName
                                        }
                                        isProcessing = false

                                        if (textureName != null) {
                                            val base = wallName.replace(" ", "_")
                                            val textureFile = File(context.cacheDir, "${base}_Albedo.png")
                                            val processedFile = File(context.cacheDir, "${base}_Processed.jpg")

                                            // 👇 carpeta donde TextureProcessor descomprime el pack
                                            val packPath = File(context.filesDir, "pbrpacks/${textureName}").absolutePath

                                            openPreview(
                                                textureFile.absolutePath,
                                                processedFile.absolutePath,
                                                wallName,
                                                textureName,  // packName real
                                                packPath      // ruta real del pack
                                            )
                                        } else {
                                            Toast.makeText(context, "❌ No se pudo determinar textura.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                ) {
                    Text("Capturar textura de $wallName")
                }

                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        } else {
            Text("Permiso de cámara requerido.")
        }
    }
}