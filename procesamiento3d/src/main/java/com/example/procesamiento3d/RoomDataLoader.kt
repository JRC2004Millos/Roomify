package com.example.procesamiento3d

import android.content.Context
import android.os.FileObserver
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset

data class WallInfo(
    val label: String,
    val from: String,
    val to: String,
    val direction: String
)

object RoomDataLoader {
    private const val FILE_NAME = "room_data.json"

    /** Ruta donde Unity (Application.persistentDataPath) suele guardar en Android */
    fun runtimeJsonFile(context: Context): File {
        // /storage/emulated/0/Android/data/<paquete>/files/room_data.json
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, FILE_NAME)
    }

    /** True si el JSON ya está disponible y con tamaño > 0 */
    fun isRuntimeJsonReady(context: Context): Boolean {
        val f = runtimeJsonFile(context)
        return f.exists() && f.isFile && f.length() > 0L
    }

    /** Lee y parsea SOLO el JSON runtime. Lanza excepción si no existe o está inválido. */
    fun loadWallsRuntime(context: Context): List<WallInfo> {
        val file = runtimeJsonFile(context)
        require(file.exists()) { "room_data.json no encontrado aún. Ejecuta la medición en Unity." }
        val json = file.readText(Charset.defaultCharset())
        return parseWalls(json)
    }

    /** Parser tolerante para walls; añade Piso y Techo al final (pública, no duplicar) */
    fun parseWalls(jsonString: String): List<WallInfo> {
        val json = JSONObject(jsonString)
        val wallsArr: JSONArray = json.optJSONArray("walls") ?: JSONArray()
        val result = mutableListOf<WallInfo>()

        for (i in 0 until wallsArr.length()) {
            val w = wallsArr.optJSONObject(i) ?: continue
            val from = w.optString("from", "")
            val to = w.optString("to", "")
            val dir = w.optString("direction", "unknown")
            val label = buildString {
                append("Pared")
                if (from.isNotBlank() && to.isNotBlank()) append(" de $from a $to")
                if (dir.isNotBlank()) append(" ($dir)")
            }
            result.add(WallInfo(label, from, to, dir))
        }

        result.add(WallInfo("piso", "all", "all", "floor"))
        result.add(WallInfo("techo", "all", "all", "ceiling"))
        return result
    }

    /** Fallback a assets si aún no existe el runtime */
    fun loadWallsFromAssets(context: Context): List<WallInfo> {
        return try {
            val json = context.assets.open("walls.json").use { it.readBytes().toString(Charsets.UTF_8) }
            parseWalls(json)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /** Preferir runtime (Unity) y caer a assets si aún no existe */
    fun loadWalls(context: Context): List<WallInfo> {
        return if (isRuntimeJsonReady(context)) loadWallsRuntime(context)
        else loadWallsFromAssets(context)
    }

    /** Útil para debug rápido */
    fun debugWhere(context: Context): String {
        val f = runtimeJsonFile(context)
        return "runtime: ${f.absolutePath} (exists=${f.exists()}, size=${if (f.exists()) f.length() else 0})"
    }
}

/** Observa SOLO la carpeta runtime y dispara callback cuando aparece/actualiza room_data.json */
class RoomJsonObserver(
    context: Context,
    private val onJsonReadyOrUpdated: () -> Unit
) : FileObserver(
    getWatchDir(context).absolutePath,
    FileObserver.CREATE or FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO
) {
    companion object {
        private fun getWatchDir(context: Context): File {
            return context.getExternalFilesDir(null) ?: context.filesDir
        }
        private const val TARGET = "room_data.json"
    }

    override fun onEvent(event: Int, path: String?) {
        if (path?.endsWith(TARGET) == true) {
            // Reacciona cuando termina de escribirse o se mueve (rename atómico)
            if (event == FileObserver.CLOSE_WRITE || event == FileObserver.MOVED_TO || event == FileObserver.CREATE) {
                onJsonReadyOrUpdated()
            }
        }
    }
}
