package com.example.ecotherms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.android.gms.tflite.java.TfLite
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PlantScannerActivity : BaseActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnCapture: Button
    private lateinit var tvResult: TextView
    private lateinit var navHome: LinearLayout
    private lateinit var navChart: LinearLayout
    private lateinit var navPlants: LinearLayout
    private lateinit var navProfile: LinearLayout

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else tvResult.text = "Permissão de câmera negada"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plant_scanner)

        previewView = findViewById(R.id.previewView)
        btnCapture  = findViewById(R.id.btnCapture)
        tvResult    = findViewById(R.id.tvResult)
        navHome     = findViewById(R.id.navHome)
        navChart    = findViewById(R.id.navChart)
        navPlants   = findViewById(R.id.navPlants)
        navProfile  = findViewById(R.id.navProfile)

        cameraExecutor = Executors.newSingleThreadExecutor()

        btnCapture.setOnClickListener { capturePhoto() }

        TfLite.initialize(this)
            .addOnSuccessListener { tvResult.text = "Pronto para identificar" }
            .addOnFailureListener { e -> tvResult.text = "Erro TFLite: ${e.message}" }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera()
        else requestPermission.launch(Manifest.permission.CAMERA)

        configurarNavBar()
        configurarDrawer()
    }

    private fun configurarNavBar() {
        navHome.setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        navChart.setOnClickListener {
            startActivity(
                Intent(this, MainActivityDados::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        navPlants.setOnClickListener {
            // já está aqui, sem ação
        }
        navProfile.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    // ─── Drawer (menu lateral) ──────────────────────────────────────────────────
    private fun configurarDrawer() {
        val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)

        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }

        findViewById<android.widget.ImageButton>(R.id.btnNotifications).setOnClickListener {
            android.widget.Toast.makeText(this, "Nenhuma notificação por enquanto", android.widget.Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.drawerItemHistory).setOnClickListener {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            startActivity(Intent(this, PlantHistoryActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.drawerItemProfile).setOnClickListener {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.drawerItemPreferences).setOnClickListener {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            startActivity(Intent(this, PreferencesActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.drawerItemSettings).setOnClickListener {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.drawerItemLogout).setOnClickListener {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            Firebase.auth.signOut()
            val intent = Intent(this, MainActivityLogin::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().setJpegQuality(70).build()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        btnCapture.isEnabled = false
        val capture = imageCapture ?: run { btnCapture.isEnabled = true; return }

        val plantPhotosDir = File(filesDir, "plant_photos").apply { mkdirs() }
        val photoFile = File(plantPhotosDir, "scan_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(outputOptions, cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    runOnUiThread { tvResult.text = "Identificando planta..." }

                    val apiKey = BuildConfig.PLANTNET_API_KEY
                    val api = com.example.ecotherms.remote.PlantNetClient.create()

                    val part = okhttp3.MultipartBody.Part.createFormData(
                        "images", photoFile.name,
                        photoFile.asRequestBody("image/jpeg".toMediaType())
                    )
                    val organsBody = "flower".toRequestBody("text/plain".toMediaType())

                    api.identify(apiKey = apiKey, lang = "pt",
                        image = part, organs = organsBody
                    ).enqueue(object : retrofit2.Callback<com.example.ecotherms.remote.PlantNetResponse> {

                        override fun onResponse(
                            call: retrofit2.Call<com.example.ecotherms.remote.PlantNetResponse>,
                            response: retrofit2.Response<com.example.ecotherms.remote.PlantNetResponse>
                        ) {
                            runOnUiThread {
                                if (!response.isSuccessful) {
                                    val details = response.errorBody()?.string()
                                    tvResult.text = when (response.code()) {
                                        401, 403 -> "Chave de API inválida.\n$details"
                                        429      -> "Cota excedida. Tente amanhã.\n$details"
                                        400      -> "Foto ou parâmetro inválido.\n$details"
                                        else     -> "Erro HTTP ${response.code()}\n$details"
                                    }
                                    btnCapture.isEnabled = true
                                    return@runOnUiThread
                                }

                                val best = response.body()?.results?.firstOrNull()
                                if (best == null) {
                                    tvResult.text = "Sem resultado. Tente mais perto da planta."
                                    btnCapture.isEnabled = true
                                } else {
                                    val common = best.species.commonNames?.firstOrNull() ?: "-"
                                    startActivity(
                                        Intent(this@PlantScannerActivity, PlantResultActivity::class.java).apply {
                                            putExtra("photoPath",      photoFile.absolutePath)
                                            putExtra("scientificName", best.species.scientificNameWithoutAuthor)
                                            putExtra("commonName",     common)
                                            putExtra("score",          String.format(java.util.Locale.US, "%.2f", best.score))
                                        }
                                    )
                                    btnCapture.isEnabled = true
                                }
                            }
                        }

                        override fun onFailure(
                            call: retrofit2.Call<com.example.ecotherms.remote.PlantNetResponse>,
                            t: Throwable
                        ) {
                            runOnUiThread {
                                tvResult.text = when (t) {
                                    is java.net.UnknownHostException   -> "Sem internet."
                                    is java.net.SocketTimeoutException -> "Conexão lenta."
                                    else -> "Falha: ${t.message}"
                                }
                                btnCapture.isEnabled = true
                            }
                        }
                    })
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        tvResult.text = "Erro ao capturar: ${exception.message}"
                        btnCapture.isEnabled = true
                    }
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}