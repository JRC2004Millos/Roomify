package com.example.roomify.storage

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.Locale

/**
 * Store centralizado de asignaciones pared -> (pack, path).
 * - Crea el JSON si no existe.
 * - Guarda "path" (carpeta local donde están las texturas del pack o, en su defecto, la carpeta del Albedo mostrado).
 * - Lee JSONs viejos que tuvieran "zip" y los mapea a path=null (retro-compatibilidad).
 *
 * Formato JSON nuevo:
 * {
 *   "project": "Roomify",
 *   "items": [
 *     { "wall": "Pared A a B", "pack": "Madera_Roble", "path": "/storage/emulated/0/Android/data/<pkg>/files/packs/Madera_Roble" }
 *   ]
 * }
 */
object TextureAssignmentStore {

    private const val FILE_NAME = "textures_model.json"

    // ===== Normalización de nombres =====
    private fun canonical(label: String): String =
        label.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").trim()

    private fun unifySynonyms(base: String): String {
        // base debe venir ya sin paréntesis y en minúsculas
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

    // ===== Modelo en memoria =====
    private data class Assignment(
        val wall: String,
        val pack: String,
        val path: String? // carpeta local con las texturas de ese pack (o carpeta del albedo mostrado)
    )

    private val assignments: MutableMap<String, Assignment> = LinkedHashMap()

    // ===== API principal =====

    /** Inserta/actualiza una asignación individual. El 3er parámetro ahora es PATH (no zip). */
    fun put(wall: String, pack: String, path: String?) {
        assignments[keyFor(wall)] = Assignment(
            wall = wall,
            pack = pack,
            path = path?.takeIf { it.isNotBlank() }
        )
        Log.d("TextureStore", "put: '$wall' -> pack='$pack', path='${path ?: "null"}'")
    }

    /** Aplica la asignación a todas las paredes equivalentes por nombre base (quitando sufijos como "(east)"). */
    fun putForEquivalentWalls(wall: String, pack: String, path: String?, allWalls: List<String>) {
        val kBase = baseKey(wall)
        val equivalentes = allWalls.filter { baseKey(it) == kBase }
        if (equivalentes.isEmpty()) {
            put(wall, pack, path)
        } else {
            equivalentes.forEach { put(it, pack, path) }
        }
    }

    // ===== Lecturas =====
    fun isAssigned(wall: String): Boolean = assignments.containsKey(keyFor(wall))
    fun getPack(wall: String): String? = assignments[keyFor(wall)]?.pack
    fun getPathForWall(wall: String): String? = assignments[keyFor(wall)]?.path

    // Retro-compat: si algún código viejo esperaba "zip", lo resolvemos como "path"
    @Deprecated("Usa getPathForWall")
    fun getZipForWall(wall: String): String? = getPathForWall(wall)

    /** Lista todas las asignaciones (útil para debug). */
    fun all(): List<Triple<String, String, String?>> =
        assignments.values.map { Triple(it.wall, it.pack, it.path) }

    /** Limpia el estado en memoria (NO borra archivo). */
    fun clear() = assignments.clear()

    // ===== Persistencia =====
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
                // Nuevo: "path". Antiguo: "zip" (lo aceptamos pero lo ignoramos si viene vacío).
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
            // Evita duplicados por pared (key canónico)
            assignments.values
                .distinctBy { keyFor(it.wall) }
                .forEach { a ->
                    val obj = JSONObject()
                        .put("wall", a.wall)
                        .put("pack", a.pack)
                    // Solo escribimos "path" (nuevo formato)
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

    /**
     * Si sigues la convención /Android/data/<pkg>/files/packs/<pack>,
     * esto te devuelve esa carpeta. No crea nada.
     */
    fun defaultPackDir(context: Context, pack: String): File =
        File(context.getExternalFilesDir("packs"), pack)

    /**
     * Intenta establecer el path usando la convención de packs/
     * (si existe esa carpeta). Útil cuando vienes de descarga y descompresión.
     */
    fun hintPathFromDefaultLocation(context: Context, wall: String, pack: String) {
        val dir = defaultPackDir(context, pack)
        if (dir.exists() && dir.isDirectory) {
            put(wall, pack, dir.absolutePath)
        } else {
            // Si no existe, no forzamos; quedará null hasta que la UI provea un albedo/dir.
            put(wall, pack, null)
        }
    }
}
