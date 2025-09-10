package com.example.roomify.model

data class RoomDimensions(val height: Float, val unit: String = "meters")
data class Wall(val from: String, val to: String, val distance: Float, val direction: String)
data class XY(val x: Float, val y: Float)
data class Corner(val id: String, val position: XY)

data class ObstacleDimensions(val width: Float, val depth: Float, val height: Float)
data class Obstacle(val type: String, val id: String, val dimensions: ObstacleDimensions, val attached_to: List<String>)

data class RoomData(
    val room_dimensions: RoomDimensions,
    val corners: List<Corner>,
    val walls: List<Wall>,
    val obstacles: List<Obstacle> = emptyList(),
    val origin_reference: String = "A"
)
