package com.example.roomify

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.roomify.ui.theme.RoomifyTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class LibreriaActivity : ComponentActivity() {

    data class SavedRoomModel(
        val file: File,
        val spaceName: String,
        val roomId: String,
        val lastModified: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RoomifyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ModelLibraryScreen()
                }
            }
        }
    }

    private fun loadSavedRooms(): List<SavedRoomModel> {
        val baseDir = File(filesDir, "saved_rooms")
        if (!baseDir.exists() || !baseDir.isDirectory) return emptyList()

        val files = baseDir.listFiles { f ->
            f.isFile && f.extension.equals("json", ignoreCase = true)
        } ?: emptyArray()

        return files.mapNotNull { f ->
            runCatching {
                val text = f.readText()
                val root = org.json.JSONObject(text)
                val spaceName = root.optString("spaceName", f.nameWithoutExtension)
                val roomId = root.optString("roomId", f.nameWithoutExtension)
                SavedRoomModel(
                    file = f,
                    spaceName = spaceName,
                    roomId = roomId,
                    lastModified = f.lastModified()
                )
            }.getOrNull()
        }
    }

    private fun openRoomInUnity(model: SavedRoomModel) {
        val ctx = this
        val jsonFile = model.file

        val packsRoot = File(ctx.filesDir, "pbrpacks")
        val token = System.currentTimeMillis().toString()

        val intent = Intent(ctx, com.unity3d.player.UnityPlayerActivity::class.java).apply {
            putExtra("SCENE_TO_LOAD", "RenderScene")
            putExtra("ROOM_LAYOUT_PATH", jsonFile.absolutePath)
            putExtra("INTENT_TOKEN", token)
        }

        startActivity(intent)
        finish()
    }

    @Composable
    fun ModelLibraryScreen() {
        val context = LocalContext.current
        var searchText by remember { mutableStateOf("") }
        var sortDescending by remember { mutableStateOf(true) }

        val modelList = remember { mutableStateListOf<SavedRoomModel>() }

        // Cargar desde disco al entrar a la pantalla
        LaunchedEffect(Unit) {
            val loaded = loadSavedRooms()
            modelList.clear()
            modelList.addAll(loaded)
        }

        // Filtrar + ordenar
        val filteredSorted = remember(modelList, searchText, sortDescending) {
            modelList
                .filter {
                    it.spaceName.contains(searchText, ignoreCase = true) ||
                            it.roomId.contains(searchText, ignoreCase = true)
                }
                .sortedBy { it.lastModified }
                .let { if (sortDescending) it.reversed() else it }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Espacios guardados",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text("Buscar", color = MaterialTheme.colorScheme.primary) },
                placeholder = { Text("Buscar por nombre o ID", color = MaterialTheme.colorScheme.primary) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Buscar",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.secondary,
                    focusedTextColor = MaterialTheme.colorScheme.primary,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Botón de ordenamiento
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (sortDescending) "Orden: Más reciente" else "Orden: Más antiguo",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                TextButton(onClick = { sortDescending = !sortDescending }) {
                    Text(if (sortDescending) "▼ Recientes" else "▲ Antiguos")
                }
            }

            Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.secondary)

            if (filteredSorted.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_roomify_logo),
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Aún no has guardado ningún espacio",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Escanea y diseña un espacio para comenzar",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSorted) { model ->
                        SavedRoomCard(
                            model = model,
                            onClick = {
                                // Llamamos al método de la Activity
                                openRoomInUnity(model)
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SavedRoomCard(
        model: SavedRoomModel,
        onClick: () -> Unit
    ) {
        val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
        val dateText = remember(model.lastModified) {
            sdf.format(Date(model.lastModified))
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = model.spaceName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "ID: ${model.roomId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Guardado: $dateText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = model.file.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}