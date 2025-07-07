package com.example.roomify

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.procesamiento3d.TextureProcessor
import java.io.ByteArrayOutputStream
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

    fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
        val yuv = out.toByteArray()
        return BitmapFactory.decodeByteArray(yuv, 0, yuv.size)
    }

    fun Bitmap.centerCrop(cropWidth: Int, cropHeight: Int): Bitmap {
        val x = (width - cropWidth) / 2
        val y = (height - cropHeight) / 2
        return Bitmap.createBitmap(this, x, y, cropWidth, cropHeight)
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
        val latestBitmap = remember { mutableStateOf<Bitmap?>(null) }

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

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalyzer.setAnalyzer(cameraExecutor, { imageProxy ->
                    val bitmap = imageProxy.toBitmap()
                    val centerCropped = bitmap.centerCrop(256, 256)
                    latestBitmap.value = centerCropped
                    imageProxy.close()
                })

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture,
                        imageAnalyzer
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

                // 🧱 Previsualización tileada
                latestBitmap.value?.let { texture ->
                    Canvas(
                        modifier = Modifier
                            .size(128.dp)
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        val shader = ImageShader(texture.asImageBitmap(), TileMode.Repeated, TileMode.Repeated)
                        drawRect(
                            brush = ShaderBrush(shader),
                            size = size,
                            style = Fill
                        )
                    }
                }

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

                                    val rutaTextura = File(
                                        context.cacheDir,
                                        "textura_${photoFile.nameWithoutExtension}.png"
                                    ).absolutePath

                                    val ok = TextureProcessor.generarTexturaDesdeRuta(photoFile.absolutePath, rutaTextura)

                                    if (ok) {
                                        Log.d("CaptureActivity", "✅ Textura generada en: $rutaTextura")
                                    } else {
                                        Log.e("CaptureActivity", "❌ Error al generar textura")
                                    }

                                    Toast.makeText(
                                        context,
                                        "Imagen $photoCount guardada:\n${photoFile.absolutePath}",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    if (photoCount == totalFotos) {
                                        Toast.makeText(
                                            context,
                                            "✅ Escaneo completado. Se tomaron $totalFotos fotos.",
                                            Toast.LENGTH_LONG
                                        ).show()
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