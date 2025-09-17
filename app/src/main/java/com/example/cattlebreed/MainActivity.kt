package com.example.cattlebreed

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ai.onnxruntime.*
import java.nio.FloatBuffer

class MainActivity : AppCompatActivity() {

    private lateinit var env: OrtEnvironment
    private lateinit var session: OrtSession
    private lateinit var labels: List<String>
    private lateinit var imageView: ImageView
    private lateinit var resultView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        resultView = findViewById(R.id.resultView)

        env = OrtEnvironment.getEnvironment()
        val modelBytes = assets.open("model.onnx").readBytes()
        session = env.createSession(modelBytes)

        labels = assets.open("classes.txt").bufferedReader().readLines()

        findViewById<Button>(R.id.pickButton).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(intent, 100)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            val bitmap = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
            imageView.setImageBitmap(bitmap)
            val input = preprocess(bitmap)
            val prediction = runInference(input)
            resultView.text = "Prediction: $prediction"
        }
    }

    private fun preprocess(bitmap: Bitmap): FloatArray {
        val size = 224
        val resized = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        val floats = FloatArray(3 * size * size)
        var idx = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                val pixel = resized.getPixel(x, y)
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f
                floats[idx] = (r - mean[0]) / std[0]; idx++
                floats[idx] = (g - mean[1]) / std[1]; idx++
                floats[idx] = (b - mean[2]) / std[2]; idx++
            }
        }
        return floats
    }

    private fun runInference(input: FloatArray): String {
        val shape = longArrayOf(1, 3, 224, 224)
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape)
        val result = session.run(mapOf(session.inputNames.iterator().next() to tensor))
        val scores = (result[0].value as Array<FloatArray>)[0]
        val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: 0
        return labels[maxIdx]
    }
}
