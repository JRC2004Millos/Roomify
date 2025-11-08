package com.example.roomify

import android.Manifest
import android.app.Activity
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
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.unity3d.player.UnityPlayerActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.Icons
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api

class CaptureActivity : ComponentActivity() {

    private lateinit var imageCapture: ImageCapture

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.loadLibrary("opencv_java4")

        setContent {
            com.example.roomify.ui.theme.RoomifyTheme {
                CaptureScreen()
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
        LaunchedEffect(refreshKey) {
            TextureAssignmentStore.loadJson(context)
        }

        var walls by remember {
            mutableStateOf(
                com.example.procesamiento3d.RoomDataLoader.loadWalls(context)
            )
        }

        // recargar walls cuando cambie refreshKey
        LaunchedEffect(refreshKey) {
            walls = runCatching {
                com.example.procesamiento3d.RoomDataLoader.loadWallsRuntime(context)
            }.getOrElse {
                com.example.procesamiento3d.RoomDataLoader.loadWalls(context)
            }
        }

        Box(Modifier.fillMaxSize()) {
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

                // Estado para alternar vista
                var useMap by remember { mutableStateOf(true) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (useMap) "Mapa 2D" else "Lista", modifier = Modifier.weight(1f))
                    Switch(checked = useMap, onCheckedChange = { useMap = it })
                }

                if (useMap) {
                    fun handleTapForLabel(targetLabel: String) {
                        TextureAssignmentStore.loadJson(context)

                        fun canonical(name: String) =
                            name.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").trim()

                        val safe = targetLabel.replace(" ", "_")
                        val thisAlbedo = File(context.cacheDir, "${safe}_Albedo.png")

                        // 1) si ya hay textura cacheada → preview directo
                        if (thisAlbedo.exists()) {
                            openPreview(
                                thisAlbedo.absolutePath,
                                File(context.cacheDir, "${safe}_Processed.jpg").absolutePath,
                                targetLabel, null, null
                            )
                            return
                        }

                        // 2) intentar reutilizar pack de otra pared con el mismo canonical
                        val allWalls = runCatching {
                            com.example.procesamiento3d.RoomDataLoader.loadWallsRuntime(context)
                        }.getOrElse {
                            com.example.procesamiento3d.RoomDataLoader.loadWalls(context)
                        }
                        val baseCanon = canonical(targetLabel)
                        val packForThis = TextureAssignmentStore.getPack(targetLabel)
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

                                    val reusedPackName = packForThis
                                    val reusedPackPath = File(context.filesDir, "pbrpacks/$reusedPackName").absolutePath

                                    openPreview(
                                        thisAlbedo.absolutePath,
                                        thisProcessed.takeIf { it.exists() }?.absolutePath,
                                        canonicalSurface(targetLabel),
                                        reusedPackName,
                                        reusedPackPath
                                    )

                                    return
                                } catch (_: Exception) { /* fallback */ }
                            }
                        }

                        // 3) fallback: chooser si hay alguna textura previa, si no cámara
                        val hasAnyTexture = (context.cacheDir.listFiles { f ->
                            f.isFile && f.name.endsWith("_Albedo.png")
                        } ?: emptyArray()).isNotEmpty()

                        if (hasAnyTexture) openChooser(targetLabel) else onWallSelected(targetLabel)
                    }
                    RoomMap2D(
                        walls = walls,
                        isAssigned = { w -> TextureAssignmentStore.getPack(canonicalSurface(w.label)) != null },
                        onWallTapped = { wall -> handleTapForLabel(wall.label) },
                        onFloorTapped = { handleTapForLabel("Floor") },
                        onCeilingTapped = { handleTapForLabel("Ceiling") },
                        refreshKey = refreshKey
                    )
                } else {
                    // ===== Lista (única, sin duplicados) =====
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 88.dp) // deja espacio para el footer
                    ) {
                        items(walls) { wall: com.example.procesamiento3d.WallInfo ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clickable {
                                        TextureAssignmentStore.loadJson(context)

                                        fun canonical(name: String) =
                                            name.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").trim()

                                        val safe = wall.label.replace(" ", "_")
                                        val thisAlbedo =
                                            File(context.cacheDir, "${safe}_Albedo.png")

                                        // 1) si ya hay textura cacheada → preview directo
                                        if (thisAlbedo.exists()) {
                                            openPreview(
                                                thisAlbedo.absolutePath,
                                                File(
                                                    context.cacheDir,
                                                    "${safe}_Processed.jpg"
                                                ).absolutePath,
                                                wall.label, null, null
                                            )
                                            return@clickable
                                        }

                                        // 2) intentar reutilizar pack de otra pared
                                        val allWalls = runCatching {
                                            com.example.procesamiento3d.RoomDataLoader.loadWallsRuntime(
                                                context
                                            )
                                        }.getOrElse {
                                            com.example.procesamiento3d.RoomDataLoader.loadWalls(
                                                context
                                            )
                                        }
                                        val baseCanon = canonical(wall.label)
                                        val packForThis = TextureAssignmentStore.getPack(wall.label)
                                            ?: allWalls.firstOrNull { canonical(it.label) == baseCanon }
                                                ?.let { TextureAssignmentStore.getPack(it.label) }

                                        if (packForThis != null) {
                                            val candidate = (context.cacheDir.listFiles { f ->
                                                f.isFile && f.name.endsWith("_Albedo.png")
                                            } ?: emptyArray()).firstOrNull { f ->
                                                val srcWall =
                                                    f.name.removeSuffix("_Albedo.png")
                                                        .replace("_", " ")
                                                TextureAssignmentStore.getPack(srcWall) == packForThis
                                            }
                                            if (candidate != null) {
                                                try {
                                                    candidate.copyTo(thisAlbedo, overwrite = true)
                                                    val srcProcessed = File(
                                                        context.cacheDir,
                                                        "${candidate.name.removeSuffix("_Albedo.png")}_Processed.jpg"
                                                    )
                                                    val thisProcessed =
                                                        File(
                                                            context.cacheDir,
                                                            "${safe}_Processed.jpg"
                                                        )
                                                    if (srcProcessed.exists())
                                                        srcProcessed.copyTo(
                                                            thisProcessed,
                                                            overwrite = true
                                                        )

                                                    openPreview(
                                                        thisAlbedo.absolutePath,
                                                        thisProcessed.takeIf { it.exists() }?.absolutePath,
                                                        wall.label, null, null
                                                    )
                                                    return@clickable
                                                } catch (_: Exception) {
                                                    // fallback a chooser/cámara
                                                }
                                            }
                                        }

                                        // 3) fallback: chooser si hay alguna textura previa, si no cámara
                                        val hasAnyTexture = (context.cacheDir.listFiles { f ->
                                            f.isFile && f.name.endsWith("_Albedo.png")
                                        } ?: emptyArray()).isNotEmpty()

                                        if (hasAnyTexture) openChooser(wall.label) else onWallSelected(
                                            wall.label
                                        )
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(wall.label, style = MaterialTheme.typography.titleMedium)
                                        val pack = TextureAssignmentStore.getPack(wall.label)
                                        Text(
                                            text = pack ?: "Sin textura asignada",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (pack != null)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    val assigned = TextureAssignmentStore.getPack(wall.label) != null
                                    Icon(
                                        imageVector = if (assigned) Icons.Default.Check else Icons.Default.PhotoCamera,
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }
                }

                // ===== Footer con botón "Ver en 3D" =====
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val ctx = context
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { openUnityPreviewNow(ctx) },
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Ver en 3D") }
                    }
                }
            }
            }
        }

    private fun labelToKey(label: String): String {
        val l = label.trim().lowercase()
        if (l.startsWith("piso")) return "FLOOR"
        if (l.startsWith("techo")) return "CEILING"
        val idxDe = l.indexOf("de ")
        val idxA  = l.indexOf(" a ", if (idxDe >= 0) idxDe + 3 else 0)
        return if (idxDe >= 0 && idxA > idxDe && idxDe + 3 < l.length && idxA + 3 < l.length) {
            val from = l[idxDe + 3].uppercaseChar()
            val to   = l[idxA + 3].uppercaseChar()
            "$from-$to"
        } else label
    }

    private fun assignedKeys(
        context: Context,
        walls: List<com.example.procesamiento3d.WallInfo>
    ): Set<String> {
        val s = mutableSetOf<String>()
        // Muros
        for (w in walls) {
            if (TextureAssignmentStore.getPack(canonicalSurface(w.label)) != null) {
                s += labelToKey(w.label)
            }
        }
        // Piso / Techo
        if (isSurfaceAssignedAny("Floor"))   s += "FLOOR"
        if (isSurfaceAssignedAny("Ceiling")) s += "CEILING"
        return s
    }

    private data class Completeness(val isComplete: Boolean, val missing: Set<String>)

    private fun validateCompleteness(
        context: Context,
        walls: List<com.example.procesamiento3d.WallInfo>,
        requireFloor: Boolean = true,
        requireCeiling: Boolean = false
    ): Completeness {
        val expected = expectedSurfaceKeys(walls, requireFloor, requireCeiling)
        val assigned = assignedKeys(context, walls)
        val missing  = expected - assigned
        return Completeness(missing.isEmpty(), missing)
    }

    private fun expectedSurfaceKeys(
        walls: List<com.example.procesamiento3d.WallInfo>,
        requireFloor: Boolean = true,
        requireCeiling: Boolean = false
    ): Set<String> {
        val s = walls.map { labelToKey(it.label) }.toMutableSet()
        if (requireFloor) s += "FLOOR"
        if (requireCeiling) s += "CEILING"
        return s
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

    private fun openUnityPreviewNow(ctx: Context) {
        try {
            TextureAssignmentStore.saveJson(ctx)

            val jsonFile = File(ctx.getExternalFilesDir(null), "textures_model.json")
            if (!jsonFile.exists() || jsonFile.length() == 0L) {
                Log.e("CaptureActivity", "❌ textures_model.json no existe o está vacío.")
                Toast.makeText(ctx, "Error: el archivo de texturas no se generó correctamente.", Toast.LENGTH_LONG).show()
                return
            }

            val packsRoot = File(ctx.filesDir, "pbrpacks")
            val token = System.currentTimeMillis().toString()

            val intent = Intent(ctx, UnityPlayerActivity::class.java).apply {
                putExtra("SCENE_TO_LOAD", "RenderScene")
                putExtra("PBR_PACKS_ROOT", packsRoot.absolutePath)
                putExtra("TEXTURES_JSON_PATH", jsonFile.absolutePath)   // 👈 FALTA ESTO
                putExtra("INTENT_TOKEN", token)
            }

            if (jsonFile.exists() && jsonFile.length() > 0L) {
                try {
                    val root = org.json.JSONObject(jsonFile.readText())
                    val items = root.getJSONArray("items")
                    for (i in 0 until items.length()) {
                        val it = items.getJSONObject(i)
                        when (it.optString("wall")) {
                            "Floor"   -> it.put("wall", "Piso")
                            "Ceiling" -> it.put("wall", "Techo")
                        }
                    }
                    jsonFile.writeText(root.toString(2)) // re-escribe bonito
                } catch (_: Exception) {
                    // Si algo falla, dejamos el JSON original (en inglés) para no bloquear el flujo
                }
            }


            Log.d("CaptureActivity", "🎮 Lanzando Unity con RenderScene")
            ctx.startActivity(intent)
            if (ctx is Activity) ctx.finish()
        } catch (e: Exception) {
            Log.e("CaptureActivity", "⚠️ Error preparando Unity: ${e.message}", e)
            Toast.makeText(ctx, "Error al abrir Unity: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun RoomMap2D(
        walls: List<com.example.procesamiento3d.WallInfo>,
        isAssigned: (com.example.procesamiento3d.WallInfo) -> Boolean,
        onWallTapped: (com.example.procesamiento3d.WallInfo) -> Unit,
        onFloorTapped: () -> Unit,
        onCeilingTapped: () -> Unit,
        refreshKey: Int
    ) {
        val edges = remember(walls, refreshKey) {
            walls.mapNotNull { w -> parseLettersFromLabel(w.label)?.let { (a, b) -> Triple(a, b, w) } }
        }
        val nodes = remember(edges, refreshKey) { edges.flatMap { listOf(it.first, it.second) }.distinct() }
        val nodePositions = remember(nodes, refreshKey) { positionsOnCircle(nodes) }
        val assignedNodes: Set<Char> = remember(edges, refreshKey) {
            val s = mutableSetOf<Char>()
            edges.forEach { (a, b, w) ->
                if (isAssigned(w)) { s += a; s += b }
            }
            s
        }

        // Pan/zoom
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val transformState = remember {
            TransformableState { z, p, _ -> scale = (scale * z).coerceIn(0.6f, 4f); offset += p }
        }
        var lastTap by remember { mutableStateOf<Offset?>(null) }

        // Colores (fuera del Canvas)
        val colorAssigned = Color(0xFF4CAF50)
        val colorUnassigned = Color.White.copy(alpha = 0.5f)
        val colorNode = Color.White
        val bgVariant = MaterialTheme.colorScheme.surfaceVariant

        // Piso/Techo asignados
        val floorAssigned = isSurfaceAssignedAny("Floor")
        val ceilAssigned  = isSurfaceAssignedAny("Ceiling")

        // Insets UI y medidas
        val density = LocalDensity.current
        val topInsetPx = with(density) { (56.dp + 12.dp).toPx() }
        val bottomInsetPx = with(density) { (64.dp + 12.dp).toPx() }
        val r = with(density) { 12.dp.toPx() }
        val pad = with(density) { 12.dp.toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .background(bgVariant)
                .pointerInput(Unit) { detectTapGestures { pos -> lastTap = pos } }
                .transformable(transformState)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val viewSize = size
                val center = Offset(viewSize.width / 2f, viewSize.height / 2f)
                val mapRotationDeg = -45f
                val mapRotationRad = Math.toRadians(mapRotationDeg.toDouble()).toFloat()

                val toView: (Offset) -> Offset = { pModel ->
                    val pRot = rotate(pModel, mapRotationRad) // endereza el polígono
                    center + (pRot * scale) + offset
                }

                val tap = lastTap
                var tappedWall: com.example.procesamiento3d.WallInfo? = null

                // ===== DIBUJAR MUROS =====
                edges.forEach { (a, b, wall) ->
                    val pa = toView(nodePositions.getValue(a))
                    val pb = toView(nodePositions.getValue(b))
                    val assigned = isAssigned(wall)

                    // línea
                    drawLine(
                        color = if (assigned) colorAssigned else colorUnassigned,
                        start = pa, end = pb, strokeWidth = 6f
                    )

                    // ===== etiqueta rotada según la pared =====
                    val mid = Offset((pa.x + pb.x) / 2f, (pa.y + pb.y) / 2f)

                    // ángulo del segmento (en grados)
                    val angleRad = kotlin.math.atan2(pb.y - pa.y, pb.x - pa.x)
                    var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()

                    // evita texto al revés: si apunta a la izquierda, gíralo 180°
                    if (angleDeg > 90f || angleDeg < -90f) angleDeg -= 180f

                    // pinta el texto con rotación y una banda semi-transparente para legibilidad
                    val nc = drawContext.canvas.nativeCanvas
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 24f
                        isAntiAlias = true
                    }
                    val bgPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.BLACK
                        alpha = 90 // ~35% opaco
                        isAntiAlias = true
                    }

                    // medir texto
                    val label = wall.label
                    val textWidth = paint.measureText(label)
                    val textHeight = paint.fontMetrics.run { bottom - top }

                    // guardar estado, trasladar al medio y rotar
                    nc.save()
                    nc.translate(mid.x, mid.y)
                    nc.rotate(angleDeg)

                    val padH = 8f
                    val padV = 6f
                    nc.drawRoundRect(
                        -textWidth / 2f - padH,
                        -textHeight / 2f - padV,
                        textWidth / 2f + padH,
                        textHeight / 2f + padV,
                        10f, 10f,
                        bgPaint
                    )
                    nc.drawText(
                        label,
                        -textWidth / 2f,
                        - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f,
                        paint
                    )
                    nc.restore()

                    if (tap != null && tappedWall == null) {
                        val d = pointToSegmentDistance(tap, pa, pb)
                        if (d <= 36f) tappedWall = wall
                    }
                }

                nodePositions.forEach { (ch, p) ->
                    val pv = toView(p)
                    val nodeIsAssigned = assignedNodes.contains(ch)
                    drawCircle(
                        color = if (nodeIsAssigned) colorAssigned else colorNode,
                        radius = 10f,
                        center = pv
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        ch.toString(), pv.x + 12f, pv.y - 12f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 30f; isAntiAlias = true
                        }
                    )
                }

                // ===== BOTÓN TECHO (fijo) =====
                val ceilCenter = Offset(viewSize.width - pad - r, topInsetPx + pad + r)

                run {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE   // ⬅️ texto blanco
                        textSize = 24f
                        isAntiAlias = true
                    }
                    val bg = android.graphics.Paint().apply {
                        color = android.graphics.Color.BLACK
                        alpha = 110                            // banda ~43%
                        isAntiAlias = true
                    }
                    val label = "Techo"
                    val w = paint.measureText(label)
                    val h = paint.fontMetrics.run { bottom - top }
                    val px = ceilCenter.x - r - 8f - w        // ⬅️ a la izquierda del círculo
                    val py = ceilCenter.y
                    val nc = drawContext.canvas.nativeCanvas
                    nc.save()
                    nc.drawRoundRect(px - 6f, py - h/2f - 6f, px + w + 6f, py + h/2f + 6f, 10f, 10f, bg)
                    nc.drawText(label, px, py - (paint.fontMetrics.ascent + paint.fontMetrics.descent)/2f, paint)
                    nc.restore()
                }

                val centroidModel = nodePositions.values.takeIf { it.isNotEmpty() }?.let { pts ->
                    val sx = pts.sumOf { it.x.toDouble() }.toFloat()
                    val sy = pts.sumOf { it.y.toDouble() }.toFloat()
                    Offset(sx / pts.size, sy / pts.size)
                }
                val floorCenter = centroidModel?.let { toView(it) }
                    ?: Offset(pad + r, viewSize.height - bottomInsetPx - pad - r)

                drawCircle(color = if (ceilAssigned) colorAssigned else colorUnassigned, radius = r, center = ceilCenter)
                drawCircle(color = if (floorAssigned) colorAssigned else colorUnassigned, radius = r, center = floorCenter)

                run {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE   // ⬅️ texto blanco
                        textSize = 24f
                        isAntiAlias = true
                    }
                    val bg = android.graphics.Paint().apply {
                        color = android.graphics.Color.BLACK
                        alpha = 110
                        isAntiAlias = true
                    }
                    val label = "Piso"
                    val w = paint.measureText(label)
                    val h = paint.fontMetrics.run { bottom - top }
                    val px = floorCenter.x - w/2f             // debajo del círculo
                    val py = floorCenter.y + r + 20f
                    val nc = drawContext.canvas.nativeCanvas
                    nc.save()
                    nc.drawRoundRect(px - 6f, py - h - 6f, px + w + 6f, py + 6f, 10f, 10f, bg)
                    nc.drawText(label, px, py - (paint.fontMetrics.ascent + paint.fontMetrics.descent)/2f, paint)
                    nc.restore()
                }

                // ===== HIT-TEST =====
                fun hitCircle(p: Offset, c: Offset, rad: Float) =
                    (p.x - c.x)*(p.x - c.x) + (p.y - c.y)*(p.y - c.y) <= (rad + 10f)*(rad + 10f)

                if (tap != null) {
                    lastTap = null
                    when {
                        hitCircle(tap, floorCenter, r) -> Handler(Looper.getMainLooper()).post { onFloorTapped() }
                        hitCircle(tap, ceilCenter, r)  -> Handler(Looper.getMainLooper()).post { onCeilingTapped() }
                        tappedWall != null             -> Handler(Looper.getMainLooper()).post { onWallTapped(tappedWall!!) }
                    }
                }
            }
        }
    }

    private fun parseLettersFromLabel(label: String): Pair<Char, Char>? {
        // Soporta: "Pared de A a B", "Muro A-B", "A -> B", etc.
        val l = label.uppercase()
        // Intenta “de X a Y”
        Regex("""DE\s+([A-Z])\s+A\s+([A-Z])""").find(l)?.let {
            return it.groupValues[1][0] to it.groupValues[2][0]
        }
        // Intenta “X - Y”
        Regex("""\b([A-Z])\s*[-–>\u2192]\s*([A-Z])\b""").find(l)?.let {
            return it.groupValues[1][0] to it.groupValues[2][0]
        }
        return null
    }

    private fun positionsOnCircle(nodes: List<Char>): Map<Char, Offset> {
        if (nodes.isEmpty()) return emptyMap()
        val n = nodes.size
        val r = 500f
        val startAngle = -90.0
        return nodes.mapIndexed { i, ch ->
            val ang = Math.toRadians(startAngle + i * (360.0 / n))
            val x = (r * Math.cos(ang)).toFloat()
            val y = (r * Math.sin(ang)).toFloat()
            ch to Offset(x, y)
        }.toMap()
    }

    private fun pointToSegmentDistance(p: Offset, a: Offset, b: Offset): Float {
        val ax = a.x; val ay = a.y
        val bx = b.x; val by = b.y
        val px = p.x; val py = p.y

        val abx = bx - ax; val aby = by - ay
        val apx = px - ax; val apy = py - ay
        val abLen2 = abx * abx + aby * aby
        val t = if (abLen2 == 0f) 0f else ((apx * abx + apy * aby) / abLen2).coerceIn(0f, 1f)
        val cx = ax + t * abx; val cy = ay + t * aby
        val dx = px - cx; val dy = py - cy
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun rotate(p: Offset, angleRad: Float): Offset {
        val c = kotlin.math.cos(angleRad)
        val s = kotlin.math.sin(angleRad)
        return Offset(p.x * c - p.y * s, p.x * s + p.y * c)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun CaptureScreen() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Texturas del espacio") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { inner ->                       // <-- usa el padding del Scaffold
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
                    CaptureContent()
                }
            }
        }
    }

    @Composable
    private fun CaptureContent() {
        var selectedWall by remember { mutableStateOf<String?>(null) }
        val context = LocalContext.current
        var pendingTargetWall by remember { mutableStateOf<String?>(null) }
        var refreshKey by remember { mutableStateOf(0) }

        // === PREVIEW launcher ===
        val previewLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> {
                    selectedWall = null
                    refreshKey++
                }
                Activity.RESULT_FIRST_USER -> {
                    if (result.data?.getBooleanExtra("repeat", false) == true) {
                        result.data?.getStringExtra("wallName")?.let { selectedWall = it }
                    }
                }
            }
        }

        // === PREVIEW lambda ===
        val openPreview: (String?, String?, String, String?, String?) -> Unit =
            { texturePath, processedPath, wallName, packName, packPath ->
                val intent = Intent(context, PreviewTextureActivity::class.java).apply {
                    putExtra("texturePath", texturePath)
                    putExtra("processedPath", processedPath)
                    putExtra("wallName", wallName)
                    putExtra("applyToWall", canonicalSurface(wallName))
                    if (!packName.isNullOrBlank()) putExtra("packName", packName)
                    if (!packPath.isNullOrBlank()) putExtra("packPath", packPath)
                }
                previewLauncher.launch(intent)
            }

        // === CHOOSER launcher ===
        val chooserLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> {
                    val albedo = result.data?.getStringExtra("albedoPath")
                    val processed = result.data?.getStringExtra("processedPath")
                    val sourceWall = result.data?.getStringExtra("sourceWall") ?: "Textura"
                    val packName = result.data?.getStringExtra("packName")
                    val packPath = result.data?.getStringExtra("packPath")
                    val applyTo = canonicalSurface(pendingTargetWall ?: sourceWall)

                    if (albedo != null) {
                        val intent = Intent(context, PreviewTextureActivity::class.java).apply {
                            putExtra("texturePath", albedo)
                            putExtra("processedPath", processed)
                            putExtra("wallName", sourceWall)
                            putExtra("applyToWall", applyTo)
                            if (!packName.isNullOrBlank()) putExtra("packName", packName)
                            if (!packPath.isNullOrBlank()) putExtra("packPath", packPath)
                        }
                        previewLauncher.launch(intent)
                    }
                }
                Activity.RESULT_FIRST_USER -> {
                    result.data?.getStringExtra("targetWall")?.let { selectedWall = it }
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

        // === UI principal ===
        if (selectedWall == null) {
            WallListScreen(
                onWallSelected = { wallName -> selectedWall = wallName },
                openPreview = openPreview,
                openChooser = openChooser,
                refreshKey = refreshKey
            )
        } else {
            CameraTextureCapture(
                wallName = selectedWall!!,
                openPreview = openPreview
            )
        }
        }

    private fun canonicalSurface(label: String): String {
        val l = label.trim().lowercase()
        return when (l) {
            "piso", "floor"   -> "Floor"
            "techo", "ceiling"-> "Ceiling"
            else              -> label  // deja paredes tal cual ("Pared de A a B ...")
        }
    }

    private fun isSurfaceAssignedAny(name: String): Boolean {
        val canon = canonicalSurface(name) // "Floor" | "Ceiling" | etiqueta de pared
        val aliases = when (canon) {
            "Floor"   -> listOf("Floor", "FLOOR", "floor", "Piso", "piso")
            "Ceiling" -> listOf("Ceiling", "CEILING", "ceiling", "Techo", "techo")
            else      -> listOf(canon)
        }
        return aliases.any { TextureAssignmentStore.getPack(it) != null }
    }

}