package com.example.roomify.storage

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.Locale

object TextureAssignmentStore {

    private const val FILE_NAME = "textures_model.json"

    private fun canonical(label: String): String =
        label.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").trim()

    private fun unifySynonyms(base: String): String {
        return when (base) {
            "piso", "floor"   -> "floor"
            "techo", "ceiling"-> "ceiling"
            else              -> base
        }
    }

    private fun keyFor(label: String): String {
        var s = Normalizer.normalize(canonical(label), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase(Locale.ROOT)
            .trim()
        s = s.replace("\\s+".toRegex(), " ")
        s = unifySynonyms(s)
        return s
    }

    private fun baseKey(label: String): String = keyFor(label) // ya unifica

    private data class Assignment(
        val wall: String,
        val pack: String,
        val path: String?
    )

    private val assignments: MutableMap<String, Assignment> = LinkedHashMap()

    fun put(wall: String, pack: String, path: String?) {
        assignments[keyFor(wall)] = Assignment(
            wall = wall,
            pack = pack,
            path = path?.takeIf { it.isNotBlank() }
        )
        Log.d("TextureStore", "put: '$wall' -> pack='$pack', path='${path ?: "null"}'")
    }

    fun putForEquivalentWalls(wall: String, pack: String, path: String?, allWalls: List<String>) {
        val kBase = baseKey(wall)
        val equivalentes = allWalls.filter { baseKey(it) == kBase }
        if (equivalentes.isEmpty()) {
            put(wall, pack, path)
        } else {
            equivalentes.forEach { put(it, pack, path) }
        }
    }

    fun isAssigned(wall: String): Boolean = assignments.containsKey(keyFor(wall))
    fun getPack(wall: String): String? = assignments[keyFor(wall)]?.pack
    fun getPathForWall(wall: String): String? = assignments[keyFor(wall)]?.path

    @Deprecated("Usa getPathForWall")
    fun getZipForWall(wall: String): String? = getPathForWall(wall)

    fun all(): List<Triple<String, String, String?>> =
        assignments.values.map { Triple(it.wall, it.pack, it.path) }

    fun clear() = assignments.clear()

    fun loadJson(context: Context) = loadFromDisk(context)

    fun loadFromDisk(context: Context) {
        try {
            val file = File(context.getExternalFilesDir(null), FILE_NAME)
            if (!file.exists()) {
                Log.d("TextureStore", "No existe aún ${file.absolutePath} (se creará al guardar).")
                return
            }
            val json = JSONObject(file.readText())
            val items = json.optJSONArray("items") ?: JSONArray()

            assignments.clear()
            for (i in 0 until items.length()) {
                val it = items.getJSONObject(i)
                val wall = it.getString("wall")
                val pack = it.getString("pack")
                val path = when {
                    it.has("path") && !it.isNull("path") -> it.optString("path").takeIf { p -> p.isNotBlank() }
                    it.has("zip") && !it.isNull("zip") -> it.optString("zip").takeIf { z -> z.isNotBlank() } // legacy -> lo tomamos como "path" si era ya una ruta
                    else -> null
                }
                assignments[keyFor(wall)] = Assignment(wall, pack, path)
            }
            Log.d("TextureStore", "JSON cargado (${assignments.size} asignaciones)")
        } catch (e: Exception) {
            Log.e("TextureStore", "loadFromDisk error: ${e.message}")
        }
    }

    fun saveJson(context: Context) {
        try {
            val arr = JSONArray()
            assignments.values
                .distinctBy { keyFor(it.wall) }
                .forEach { a ->
                    val obj = JSONObject()
                        .put("wall", a.wall)
                        .put("pack", a.pack)
                    a.path?.let { obj.put("path", it) }
                    arr.put(obj)
                }

            val root = JSONObject()
                .put("project", "Roomify")
                .put("items", arr)

            val out = File(context.getExternalFilesDir(null), FILE_NAME)
            out.writeText(root.toString(2))
            Log.d("TextureStore", "JSON guardado en: ${out.absolutePath}")
        } catch (e: Exception) {
            Log.e("TextureStore", "saveJson error: ${e.message}")
        }
    }

    fun defaultPackDir(context: Context, pack: String): File =
        File(context.getExternalFilesDir("packs"), pack)

    fun hintPathFromDefaultLocation(context: Context, wall: String, pack: String) {
        val dir = defaultPackDir(context, pack)
        if (dir.exists() && dir.isDirectory) {
            put(wall, pack, dir.absolutePath)
        } else {
            put(wall, pack, null)
        }
    }
}
