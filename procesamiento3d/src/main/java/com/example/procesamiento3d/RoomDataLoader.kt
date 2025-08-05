package com.example.procesamiento3d

import android.content.Context
import org.json.JSONObject
import java.nio.charset.Charset

data class WallInfo(
    val label: String,
    val from: String,
    val to: String,
    val direction: String
)

object RoomDataLoader {
    fun loadWallsFromAssets(context: Context): List<WallInfo> {
        val jsonString = context.assets.open("room_data.json")
            .readBytes().toString(Charset.defaultCharset())
        val jsonObject = JSONObject(jsonString)

        val walls = jsonObject.getJSONArray("walls")
        val result = mutableListOf<WallInfo>()

        for (i in 0 until walls.length()) {
            val wall = walls.getJSONObject(i)
            val from = wall.getString("from")
            val to = wall.getString("to")
            val direction = wall.getString("direction")
            val label = "Pared de $from a $to ($direction)"
            result.add(WallInfo(label, from, to, direction))
        }

        result.add(WallInfo("Piso", "all", "all", "floor"))
        result.add(WallInfo("Techo", "all", "all", "ceiling"))

        return result
    }
}
