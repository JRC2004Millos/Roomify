package com.example.roomify

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CaptureActivity : ComponentActivity() {

    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            CameraScreen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onResume() {
        super.onResume()
        try {
            System.loadLibrary("opencv_java4")
            Log.d("OpenCV", "✅ OpenCV cargado manualmente")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("OpenCV", "❌ Error al cargar OpenCV: ${e.message}")
        }
    }

    @Composable
    fun CameraScreen() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val previewView = remember { PreviewView(context) }
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

        val totalFotos = 4
        var photoCount by remember { mutableStateOf(0) }
        val capturedImagePaths = remember { mutableStateListOf<String>() }

        var hasPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted ->
                hasPermission = granted
            }
        )

        LaunchedEffect(Unit) {
            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        if (hasPermission) {
            LaunchedEffect(Unit) {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder().build()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } catch (exc: Exception) {
                    Log.e("CameraX", "Use case binding failed", exc)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                        .border(2.dp, Color.White)
                )

                Text(
                    text = "Foto ${photoCount + 1} de $totalFotos",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                )

                Button(
                    onClick = {
                        if (photoCount >= totalFotos) {
                            Toast.makeText(context, "¡Ya has capturado las $totalFotos fotos!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val photoFile = File(
                            context.cacheDir,
                            "IMG_${System.currentTimeMillis()}.jpg"
                        )

                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                        imageCapture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onError(exc: ImageCaptureException) {
                                    Toast.makeText(context, "Error: ${exc.message}", Toast.LENGTH_SHORT).show()
                                }

                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    capturedImagePaths.add(photoFile.absolutePath)
                                    photoCount++

                                    Log.d("CaptureActivity", "Ruta guardada: ${photoFile.absolutePath}")
                                    Toast.makeText(
                                        context,
                                        "Imagen ${photoCount} guardada:\n${photoFile.absolutePath}",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    if (photoCount == totalFotos) {
                                        Toast.makeText(
                                            context,
                                            "✅ Escaneo completado. Se tomaron $totalFotos fotos.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        // Aquí podrías navegar o guardar el modelo
                                    }
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                ) {
                    Text("Capturar")
                }
            }
        } else {
            Text("Se requiere permiso de cámara.")
        }
    }
}