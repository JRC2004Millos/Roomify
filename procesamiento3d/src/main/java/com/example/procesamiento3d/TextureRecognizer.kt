package com.example.procesamiento3d

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import kotlin.math.sqrt

object TextureRecognizer {

    private const val MODEL_NAME = "mobilenet_v1.tflite"
    private const val INPUT_SIZE = 224

    private lateinit var interpreter: Interpreter
    private var initialized = false

    fun initialize(context: Context) {
        if (!initialized) {
            val assetFileDescriptor = context.assets.openFd(MODEL_NAME)
            val fileInputStream = assetFileDescriptor.createInputStream()
            val byteBuffer = fileInputStream.channel.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.declaredLength
            )
            interpreter = Interpreter(byteBuffer)
            initialized = true
        }
    }

    suspend fun findMostSimilar(context: Context, inputBitmap: Bitmap): String? {
        initialize(context)

        val scaledBitmap = Bitmap.createScaledBitmap(inputBitmap, INPUT_SIZE, INPUT_SIZE, true)
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(scaledBitmap)
        val inputFeatures = tensorImage.buffer.let {
            val output = TensorBuffer.createFixedSize(intArrayOf(1, 1001), DataType.FLOAT32)
            interpreter.run(it, output.buffer.rewind())
            output.floatArray
        }

        val texturesDir = context.assets.list("textures") ?: return null
        var bestMatch: String? = null
        var bestScore = Float.MAX_VALUE

        for (textureName in texturesDir) {
            val textureStream = context.assets.open("textures/$textureName")
            val textureBitmap = BitmapFactory.decodeStream(textureStream)
            val textureScaled = Bitmap.createScaledBitmap(textureBitmap, INPUT_SIZE, INPUT_SIZE, true)

            val textureTensor = TensorImage(DataType.FLOAT32)
            textureTensor.load(textureScaled)
            val textureOutput = TensorBuffer.createFixedSize(intArrayOf(1, 1001), DataType.FLOAT32)
            interpreter.run(textureTensor.buffer, textureOutput.buffer.rewind())

            val score = cosineDistance(inputFeatures, textureOutput.floatArray)
            if (score < bestScore) {
                bestScore = score
                bestMatch = textureName
            }
        }

        return bestMatch
    }

    private fun cosineDistance(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return 1f - (dot / (sqrt(normA) * sqrt(normB)))
    }
}
