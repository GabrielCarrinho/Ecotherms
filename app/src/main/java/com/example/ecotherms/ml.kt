package com.example.scannerdeplantas.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.InterpreterApi.Options.TfLiteRuntime
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.min

data class ClassificationResult(val label: String, val confidence: Float)

class PlantClassifier(private val context: Context) {

    private val labels: List<String> by lazy {
        context.assets.open("labels.txt").bufferedReader().use { it.readLines() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private val interpreter: InterpreterApi by lazy {
        val modelBuffer = loadModelFile("model.tflite")
        val options = InterpreterApi.Options().setRuntime(TfLiteRuntime.FROM_SYSTEM_ONLY)
        InterpreterApi.create(modelBuffer, options)
    }

    fun classify(bitmap: Bitmap): ClassificationResult {
        val inputTensor = interpreter.getInputTensor(0)
        val inShape = inputTensor.shape() // normalmente [1, h, w, 3]
        val h = inShape[1]
        val w = inShape[2]

        val resized = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val input = bitmapToFloatBuffer(resized, w, h)

        val outputTensor = interpreter.getOutputTensor(0)
        val outShape = outputTensor.shape()
        val classes = outShape.last()

        val output = Array(1) { FloatArray(classes) }

        interpreter.run(input, output)

        val probs = output[0]
        var best = 0
        for (i in 1 until probs.size) if (probs[i] > probs[best]) best = i

        val label =
            if (best in labels.indices) labels[best]
            else "Classe_$best"

        return ClassificationResult(label, probs[best])
    }

    private fun loadModelFile(assetName: String): ByteBuffer {
        val afd = context.assets.openFd(assetName)
        FileInputStream(afd.fileDescriptor).use { fis ->
            val channel = fis.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }

    private fun bitmapToFloatBuffer(bitmap: Bitmap, w: Int, h: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * w * h * 3).apply {
            order(ByteOrder.nativeOrder())
        }

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var p = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = pixels[p++]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                buffer.putFloat(r / 255f)
                buffer.putFloat(g / 255f)
                buffer.putFloat(b / 255f)
            }
        }

        buffer.rewind()
        return buffer
    }
}