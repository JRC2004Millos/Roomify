package com.example.roomify

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.roomify.ui.theme.RoomifyTheme
import com.example.procesamiento3d.MedicionActivity  // ✅ IMPORTANTE

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RoomifyTheme {
                MainMenu()
            }
        }
    }

    @Composable
    fun MainMenu() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_roomify_logo),
                            contentDescription = "Roomify Logo",
                            modifier = Modifier
                                .size(100.dp)
                                .padding(top = 8.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                        )

                        Text(
                            text = "Bienvenido a Roomify",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "Escanea y gestiona tus espacios en 3D",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Botón para lanzar Camera
                        Button(
                            onClick = {
                                startActivity(Intent(this@MainActivity, CaptureActivity::class.java))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Escanear nueva habitación")
                        }

                        // Botón para librería de modelos
                        OutlinedButton(
                            onClick = {
                                // startActivity(Intent(this@MainActivity, ModelLibraryActivity::class.java))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Filled.Folder, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Librería de modelos")
                        }

                        // ✅ Botón para lanzar MedicionActivity
                        OutlinedButton(
                            onClick = {
                                startActivity(Intent(this@MainActivity, MedicionActivity::class.java))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Medir habitación (modo puntos)")
                        }
                    }
                }
            }
        }
    }
}
