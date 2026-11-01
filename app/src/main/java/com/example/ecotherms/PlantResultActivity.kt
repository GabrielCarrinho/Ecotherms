package com.example.ecotherms

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import java.io.File
import android.widget.Toast
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlantResultActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plant_result)

        val ivPhoto      = findViewById<ImageView>(R.id.ivPhoto)
        val tvScientific = findViewById<TextView>(R.id.tvScientific)
        val tvCommon     = findViewById<TextView>(R.id.tvCommon)
        val tvScore      = findViewById<TextView>(R.id.tvScore)
        val btnSaveHistory =
            findViewById<LinearLayout>(R.id.btnSaveHistory)

        val scientificName =
            intent.getStringExtra("scientificName") ?: "-"

        val commonName =
            intent.getStringExtra("commonName") ?: "-"

        val score =
            intent.getStringExtra("score") ?: "-"

        tvScientific.text = scientificName
        tvCommon.text = commonName
        val scorePercent = score.replace(',', '.').toDoubleOrNull()?.let { (it * 100).toInt() }
        tvScore.text = if (scorePercent != null) "$scorePercent%" else score

        val photoPath = intent.getStringExtra("photoPath")
        if (!photoPath.isNullOrBlank()) {
            val file = File(photoPath)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) ivPhoto.setImageBitmap(fixRotation(file.absolutePath, bitmap))
            }
        }
        btnSaveHistory.setOnClickListener {

            savePlantToHistory(
                scientificName,
                commonName,
                score,
                photoPath ?: ""
            )
        }


        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        findViewById<LinearLayout>(R.id.navChart).setOnClickListener {
            startActivity(
                Intent(this, MainActivityDados::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        findViewById<LinearLayout>(R.id.navPlants).setOnClickListener {
            startActivity(
                Intent(this, PlantScannerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

    }

    private fun fixRotation(path: String, bitmap: Bitmap): Bitmap {
        val exif = ExifInterface(path)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
        val angle = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90  -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (angle == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(angle) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun savePlantToHistory(
        scientificName: String,
        commonName: String,
        score: String,
        photoPath: String
    ) {

        val userKey = Firebase.auth.currentUser?.uid

        if (userKey == null) {

            Toast.makeText(
                this,
                "Usuário não encontrado",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val date =
            SimpleDateFormat(
                "dd/MM/yyyy",
                Locale.getDefault()
            ).format(Date())

        val plant = PlantHistory(
            scientificName,
            commonName,
            score,
            photoPath,
            date
        )

        FirebaseDatabase
            .getInstance()
            .reference
            .child("users")
            .child(userKey)
            .child("history")
            .push()
            .setValue(plant)
            .addOnSuccessListener {

                Toast.makeText(
                    this,
                    "Planta salva!",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->

                Toast.makeText(
                    this,
                    "Erro: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}