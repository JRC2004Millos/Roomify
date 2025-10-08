package com.example.procesamiento3d.storage

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class WallTexture(val wall: String, val pack: String, val zip: String?)

object TextureAssignmentStore {

    // Mantenemos un mapa en memoria: "nombre de pared" -> asignación
    private val assignments = linkedMapOf<String, WallTexture>()

    /** Registra/actualiza la textura asignada a una pared (o piso/techo). */
    fun put(wall: String, pack: String, zip: String?) {
        assignments[wall] = WallTexture(wall, pack, zip)
    }

    /**
     * Guarda el JSON en:
     *   /Android/data/<tu.paquete>/files/textures_model.json
     *
     * Usamos getExternalFilesDir(null). Si algún dispositivo devuelve null,
     * caemos a filesDir (interna) para no fallar.
     */
    fun saveJson(context: Context): File? {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val outFile = File(baseDir, "textures_model.json")
        return try {
            val root = JSONObject()
            root.put("project", "Roomify")

            val items = JSONArray()
            assignments.values.forEach { w ->
                val obj = JSONObject()
                obj.put("wall", w.wall)
                obj.put("pack", w.pack)
                // si zip es null, escribimos JSON null
                if (w.zip == null) obj.put("zip", JSONObject.NULL) else obj.put("zip", w.zip)
                items.put(obj)
            }
            root.put("items", items)

            outFile.writeText(root.toString(2), Charsets.UTF_8)
            Log.d("TextureStore", "✅ JSON escrito en: ${outFile.absolutePath}")
            outFile
        } catch (e: Exception) {
            Log.e("TextureStore", "❌ Error guardando JSON: ${e.message}")
            null
        }
    }

    /** (Opcional) Borrar asignaciones en memoria. */
    fun clear() = assignments.clear()
}
