package com.example.roomify

import android.Manifest
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.procesamiento3d.RoomDataLoader
import com.example.procesamiento3d.TextureProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.util.Log

class CaptureActivity : ComponentActivity() {

    private lateinit var imageCapture: ImageCapture

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.loadLibrary("opencv_java4")

        setContent {
            var selectedWall by remember { mutableStateOf<String?>(null) }

            if (selectedWall == null) {
                WallListScreen { wallName -> selectedWall = wallName }
            } else {
                CameraTextureCapture(wallName = selectedWall!!)
            }
        }
    }

    @Composable
    fun WallListScreen(onWallSelected: (String) -> Unit) {
        val context = LocalContext.current
        val walls = remember { RoomDataLoader.loadWallsFromAssets(context) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            items(walls.size) { index ->
                val wall = walls[index]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable {
                            val albedoFile =
                                File(context.cacheDir, "${wall.label.replace(" ", "_")}_Albedo.png")
                            if (albedoFile.exists()) {
                                val intent =
                                    Intent(context, PreviewTextureActivity::class.java).apply {
                                        putExtra("texturePath", File(context.cacheDir, "${wall.label.replace(" ", "_")}_Albedo.png").absolutePath)
                                        putExtra("wallName", wall.label)
                                        putExtra("processedPath", File(context.cacheDir, "${wall.label.replace(" ", "_")}_Processed.jpg").absolutePath)
                                    }
                                context.startActivity(intent)
                            } else {
                                onWallSelected(wall.label)
                            }
                        }
                ) {
                    Text(
                        text = wall.label,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    @Composable
    fun CameraTextureCapture(wallName: String) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val previewView = remember { PreviewView(context) }

        var hasPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
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
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder().build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

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
                                    CoroutineScope(Dispatchers.IO).launch {
                                        isProcessing = true

                                        val originalBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)

                                        // 🔄 Procesar, recortar, comparar y guardar resultados
                                        val (_, textureName) = TextureProcessor.procesarYCompararTextura(
                                            context = context,
                                            previewView = previewView,
                                            originalBitmap = originalBitmap,
                                            wallName = wallName
                                        )
                                        Log.d("CaptureActivity", "🎯 Resultado de textura sugerida: $textureName")

                                        withContext(Dispatchers.Main) {
                                            isProcessing = false

                                            if (textureName != null) {
                                                val textureFile = File(context.cacheDir, "${wallName.replace(" ", "_")}_Albedo.png")
                                                val processedFile = File(context.cacheDir, "${wallName.replace(" ", "_")}_Processed.jpg")

                                                val intent = Intent(context, PreviewTextureActivity::class.java).apply {
                                                    putExtra("texturePath", textureFile.absolutePath)
                                                    putExtra("processedPath", processedFile.absolutePath)
                                                    putExtra("wallName", wallName)
                                                }
                                                context.startActivity(intent)
                                                if (context is ComponentActivity) context.finish()
                                            } else {
                                                Toast.makeText(context, "❌ No se recibió textura", Toast.LENGTH_LONG).show()
                                            }
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
