package com.example.cattlebreed

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.cardview.widget.CardView
import ai.onnxruntime.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.BufferedInputStream
import java.nio.FloatBuffer
import java.util.*
import kotlin.math.exp

class MainActivity : AppCompatActivity() {

    private lateinit var env: OrtEnvironment
    private lateinit var session: OrtSession
    private lateinit var imageView: ImageView
    private lateinit var selectImageBtn: MaterialButton
    private lateinit var takePictureBtn: MaterialButton
    private lateinit var predictBtn: MaterialButton
    private lateinit var resultCard: MaterialCardView
    private lateinit var breedName1: TextView
    private lateinit var confidence1: TextView
    private lateinit var breedName2: TextView
    private lateinit var confidence2: TextView
    private lateinit var breedName3: TextView
    private lateinit var confidence3: TextView
    private lateinit var progressBar: ProgressBar
    
    private var selectedBitmap: Bitmap? = null
    
    private val cattleBreeds = arrayOf(
        "Alambadi", "Amritmahal", "Ayrshire", "Banni", "Bargur", "Bhadawari",
        "Brown Swiss", "Dangi", "Deoni", "Gir", "Gujjar", "Hallikar", "Hariana",
        "Holstein Friesian", "Jersey", "Kankrej", "Krishna Valley", "Malnad Gidda",
        "Malvi", "Mewati", "Nagori", "Nelore", "Ongole", "Pulikulam", "Red Sindhi",
        "Rathi", "Sahiwal", "Simmental", "Tharparkar", "Vechur", "Abigar", "Afar",
        "Ankole Watusi", "Banteng", "Brahman", "Chianina", "Highland", "Limousin",
        "Nguni", "Shorthorn", "Zebu"
    )
    
    companion object {
        private const val REQUEST_IMAGE_PICK = 1
        private const val REQUEST_IMAGE_CAPTURE = 2
        private const val REQUEST_CAMERA_PERMISSION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set status bar color
        window.statusBarColor = Color.parseColor("#1A237E")
        
        setContentView(R.layout.activity_main)
        
        initializeViews()
        setupGradientBackground()
        initializeModel()
        setupClickListeners()
    }
    
    private fun initializeViews() {
        imageView = findViewById(R.id.imageView)
        selectImageBtn = findViewById(R.id.selectImageBtn)
        takePictureBtn = findViewById(R.id.takePictureBtn)
        predictBtn = findViewById(R.id.predictBtn)
        resultCard = findViewById(R.id.resultCard)
        breedName1 = findViewById(R.id.breedName1)
        confidence1 = findViewById(R.id.confidence1)
        breedName2 = findViewById(R.id.breedName2)
        confidence2 = findViewById(R.id.confidence2)
        breedName3 = findViewById(R.id.breedName3)
        confidence3 = findViewById(R.id.confidence3)
        progressBar = findViewById(R.id.progressBar)
        
        // Initially hide results
        resultCard.visibility = View.GONE
        predictBtn.isEnabled = false
    }
    
    private fun setupGradientBackground() {
        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#E3F2FD"),
                Color.parseColor("#BBDEFB"),
                Color.parseColor("#90CAF9")
            )
        )
        findViewById<View>(R.id.mainLayout).background = gradientDrawable
    }
    
    private fun initializeModel() {
        try {
            env = OrtEnvironment.getEnvironment()
            val modelStream = assets.open("model.onnx")
            val modelBytes = modelStream.readBytes()
            session = env.createSession(modelBytes)
            
            Toast.makeText(this, "🐄 CattlyVet Ready!", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e("ONNX", "Error loading model", e)
            Toast.makeText(this, "❌ Model loading failed", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupClickListeners() {
        selectImageBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, REQUEST_IMAGE_PICK)
        }
        
        takePictureBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            } else {
                openCamera()
            }
        }
        
        predictBtn.setOnClickListener {
            selectedBitmap?.let { bitmap ->
                classifyImage(bitmap)
            }
        }
    }
    
    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_PICK -> {
                    data?.data?.let { uri ->
                        loadImageFromUri(uri)
                    }
                }
                REQUEST_IMAGE_CAPTURE -> {
                    val imageBitmap = data?.extras?.get("data") as? Bitmap
                    imageBitmap?.let {
                        displayImage(it)
                    }
                }
            }
        }
    }
    
    private fun loadImageFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(BufferedInputStream(inputStream))
            displayImage(bitmap)
        } catch (e: Exception) {
            Log.e("Image", "Error loading image", e)
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun displayImage(bitmap: Bitmap) {
        selectedBitmap = bitmap
        imageView.setImageBitmap(bitmap)
        predictBtn.isEnabled = true
        resultCard.visibility = View.GONE
        
        // Add subtle animation
        imageView.alpha = 0f
        imageView.animate().alpha(1f).duration = 300
    }
    
    private fun classifyImage(bitmap: Bitmap) {
        progressBar.visibility = View.VISIBLE
        predictBtn.isEnabled = false
        
        // Run classification in background thread
        Thread {
            try {
                val predictions = runInference(bitmap)
                
                runOnUiThread {
                    displayResults(predictions)
                    progressBar.visibility = View.GONE
                    predictBtn.isEnabled = true
                }
                
            } catch (e: Exception) {
                Log.e("Classification", "Error during inference", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, 
                        "Classification failed: ${e.message}", Toast.LENGTH_LONG).show()
                    progressBar.visibility = View.GONE
                    predictBtn.isEnabled = true
                }
            }
        }.start()
    }
    
    private fun runInference(bitmap: Bitmap): List<Prediction> {
        // Preprocess image
        val input = preprocess(bitmap)
        
        // Create tensor
        val shape = longArrayOf(1, 3, 224, 224)
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape)
        val feed = mapOf(session.inputNames.iterator().next() to tensor)
        
        // Run inference
        val outputs = session.run(feed)
        val raw = outputs[0].value as Array<FloatArray>
        val logits = raw[0]
        
        // Apply softmax
        val probabilities = softmax(logits)
        
        // Get top 3 predictions
        val predictions = mutableListOf<Prediction>()
        for (i in probabilities.indices) {
            predictions.add(Prediction(cattleBreeds[i], probabilities[i]))
        }
        
        // Sort by confidence and take top 3
        predictions.sortByDescending { it.confidence }
        return predictions.take(3)
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

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exp = logits.map { exp((it - max).toDouble()).toFloat() }.toFloatArray()
        val sum = exp.sum()
        return exp.map { it / sum }.toFloatArray()
    }
    
    private fun displayResults(predictions: List<Prediction>) {
        resultCard.visibility = View.VISIBLE
        
        // Animate results appearance
        resultCard.alpha = 0f
        resultCard.animate().alpha(1f).duration = 500
        
        // Display top 3 predictions
        breedName1.text = predictions[0].breedName
        confidence1.text = "${(predictions[0].confidence * 100).toInt()}%"
        confidence1.setTextColor(getConfidenceColor(predictions[0].confidence))
        
        breedName2.text = predictions[1].breedName
        confidence2.text = "${(predictions[1].confidence * 100).toInt()}%"
        confidence2.setTextColor(getConfidenceColor(predictions[1].confidence))
        
        breedName3.text = predictions[2].breedName
        confidence3.text = "${(predictions[2].confidence * 100).toInt()}%"
        confidence3.setTextColor(getConfidenceColor(predictions[2].confidence))
    }
    
    private fun getConfidenceColor(confidence: Float): Int {
        return when {
            confidence > 0.7f -> Color.parseColor("#4CAF50")  // Green
            confidence > 0.3f -> Color.parseColor("#FF9800")  // Orange
            else -> Color.parseColor("#F44336")               // Red
        }
    }
    
    data class Prediction(
        val breedName: String,
        val confidence: Float
    )
}
