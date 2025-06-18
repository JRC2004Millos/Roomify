package com.example.roomify

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.roomify.OpenCVUtils.correctBitmapRotation
import com.example.roomify.ui.theme.RoomifyTheme
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CaptureActivity : ComponentActivity() {

    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            RoomifyTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CameraScreen()
                }
            }
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

        var capturedImage by remember { mutableStateOf<File?>(null) }
        var isPreviewing by remember { mutableStateOf(false) }
        var photoCount by remember { mutableStateOf(0) }
        val capturedImagePaths = remember { mutableStateListOf<String>() }

        var hasPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED
            )
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted -> hasPermission = granted }
        )

        LaunchedEffect(Unit) {
            if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
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
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                } catch (exc: Exception) {
                    Log.e("CameraX", "Use case binding failed", exc)
                }
            }

            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

                capturedImage?.let { imageFile ->
                    val bitmap = remember(imageFile) { correctBitmapRotation(imageFile) }
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Imagen capturada",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.8f)
                            .padding(16.dp)
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(onClick = {
                            photoCount++
                            isPreviewing = false
                            capturedImage = null
                        }) {
                            Text("Aceptar")
                        }

                        Button(onClick = {
                            photoCount++
                            isPreviewing = false
                            capturedImage = null
                        }) {
                            Text("Repetir")
                        }
                    }
                }

                Text(
                    text = "Foto ${photoCount + 1}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
                )

                IconButton(
                    onClick = {
                        if (photoCount == 0) {
                            Toast.makeText(context, "Debes capturar al menos una imagen.", Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
                        val intent = Intent(context, CargaActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        if (context is ComponentActivity) context.finish()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Finalizar captura", tint = MaterialTheme.colorScheme.onPrimary)
                }

                if (!isPreviewing) {
                    IconButton(
                        onClick = {
                            val photoFile = File(context.cacheDir, "IMG_${System.currentTimeMillis()}.jpg")
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
                                        capturedImage = photoFile
                                        isPreviewing = true
                                        Log.d("CaptureActivity", "Ruta guardada: ${photoFile.absolutePath}")
                                        Toast.makeText(context, "Imagen ${photoCount + 1} guardada", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(32.dp)
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Capturar", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
                    }
                }
            }
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("Se requiere permiso de cámara.", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    @Composable
    fun ProcessedImagePreview(imagePath: String) {
        val bitmap = remember(imagePath) {
            OpenCVUtils.processImageToGray(File(imagePath))
        }

        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Processed Image",
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        } ?: Text("No se pudo procesar la imagen")
    }
}
