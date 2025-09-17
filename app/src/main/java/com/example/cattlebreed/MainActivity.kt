package com.example.cattlebreed

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ai.onnxruntime.*
import java.io.BufferedInputStream
import java.nio.FloatBuffer

class MainActivity : AppCompatActivity() {

    private lateinit var env: OrtEnvironment
    private lateinit var session: OrtSession
    private lateinit var labels: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val pickButton = findViewById<Button>(R.id.pickButton)
        val imageView = findViewById<ImageView>(R.id.imageView)
        val resultView = findViewById<TextView>(R.id.resultView)

        // init ONNX Runtime
        env = OrtEnvironment.getEnvironment()
        val modelStream = assets.open("model.onnx")
        val modelBytes = modelStream.readBytes()
        session = env.createSession(modelBytes) // use default session options

        labels = assets.open("classes.txt").bufferedReader().readLines()

        pickButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(intent, 100)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val imageView = findViewById<ImageView>(R.id.imageView)
        val resultView = findViewById<TextView>(R.id.resultView)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(BufferedInputStream(inputStream))
            imageView.setImageBitmap(bitmap)
            val input = preprocess(bitmap)
            val pred = runInference(input)
            resultView.text = "Prediction: $pred"
        }
    }

    private fun preprocess(bitmap: Bitmap): FloatArray {
        val size = 224
        val resized = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        // ONNX Runtime expects NCHW float32 contiguous: [1,3,224,224]
        val floats = FloatArray(3 * size * size)
        var idx = 0
        // fill channel-wise: R then G then B
        for (c in 0..2) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val pixel = resized.getPixel(x, y)
                    val r = ((pixel shr 16) and 0xFF) / 255.0f
                    val g = ((pixel shr 8) and 0xFF) / 255.0f
                    val b = (pixel and 0xFF) / 255.0f
                    val v = when (c) {
                        0 -> (r - mean[0]) / std[0]
                        1 -> (g - mean[1]) / std[1]
                        else -> (b - mean[2]) / std[2]
                    }
                    floats[idx++] = v
                }
            }
        }
        return floats
    }

    private fun runInference(input: FloatArray): String {
        val shape = longArrayOf(1, 3, 224, 224)
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape)
        val feed = mapOf(session.inputNames.iterator().next() to tensor)
        val outputs = session.run(feed)
        val raw = outputs[0].value as Array<FloatArray>
        val scores = raw[0]
        var maxI = 0
        var maxV = scores[0]
        for (i in scores.indices) {
            if (scores[i] > maxV) { maxV = scores[i]; maxI = i }
        }
        return labels.getOrElse(maxI) { "class_$maxI" }
    }
}
