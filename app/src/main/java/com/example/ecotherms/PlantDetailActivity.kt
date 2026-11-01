package com.example.ecotherms

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.database
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream

class PlantDetailActivity : BaseActivity() {

    private lateinit var ivPhoto: ImageView
    private lateinit var etNickname: EditText
    private lateinit var tvScientificReadonly: TextView
    private lateinit var tvScoreDetail: TextView
    private lateinit var tvDateDetail: TextView

    private var plantKey: String? = null
    private var scientificName: String = ""
    private var currentPhotoPath: String = ""

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        iniciarRecorte(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plant_detail)

        ivPhoto = findViewById(R.id.ivPhoto)
        etNickname = findViewById(R.id.et_nickname)
        tvScientificReadonly = findViewById(R.id.tvScientificReadonly)
        tvScoreDetail = findViewById(R.id.tvScoreDetail)
        tvDateDetail = findViewById(R.id.tvDateDetail)

        plantKey = intent.getStringExtra("plantKey")
        scientificName = intent.getStringExtra("scientificName") ?: "-"
        val commonName = intent.getStringExtra("commonName") ?: ""
        val score = intent.getStringExtra("score") ?: "-"
        val date = intent.getStringExtra("date") ?: ""
        currentPhotoPath = intent.getStringExtra("photoPath") ?: ""

        if (plantKey == null) {
            Toast.makeText(this, "Não foi possível abrir essa planta", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvScientificReadonly.text = scientificName
        etNickname.setText(if (commonName.isBlank() || commonName == "-") "" else commonName)
        tvDateDetail.text = date

        val scorePercent = score.replace(',', '.').toDoubleOrNull()?.let { (it * 100).toInt() }
        tvScoreDetail.text = if (scorePercent != null) "$scorePercent%" else score

        carregarFoto()

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btn_delete_plant).setOnClickListener { confirmarExclusao() }
        findViewById<ImageButton>(R.id.btn_change_plant_photo).setOnClickListener { pickPhoto.launch("image/*") }
        findViewById<Button>(R.id.btn_save_plant).setOnClickListener { salvarAlteracoes() }
    }

    private fun carregarFoto() {
        if (currentPhotoPath.isNotBlank()) {
            val file = File(currentPhotoPath)
            if (file.exists()) {
                ivPhoto.setImageURI(Uri.fromFile(file))
                return
            }
        }
        ivPhoto.setImageResource(R.drawable.ic_plant)
    }

    private fun iniciarRecorte(sourceUri: Uri) {
        val destinoArquivo = File(cacheDir, "cropped_plant_${System.currentTimeMillis()}.jpg")
        val destinoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", destinoArquivo)

        val opcoes = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
            setToolbarTitle("Ajustar foto")
            setToolbarColor(ContextCompat.getColor(this@PlantDetailActivity, R.color.botao))
            setStatusBarColor(ContextCompat.getColor(this@PlantDetailActivity, R.color.botao))
            setActiveControlsWidgetColor(ContextCompat.getColor(this@PlantDetailActivity, R.color.botao))
            setToolbarWidgetColor(ContextCompat.getColor(this@PlantDetailActivity, android.R.color.white))
            setRootViewBackgroundColor(ContextCompat.getColor(this@PlantDetailActivity, R.color.verde_escuro))
            setHideBottomControls(false)
            // Sem recorte circular aqui — é uma foto retangular, não um avatar
        }

        // Sem aspect ratio fixo: recorte livre, do jeito que a pessoa quiser
        UCrop.of(sourceUri, destinoUri)
            .withOptions(opcoes)
            .start(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == UCrop.REQUEST_CROP) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val croppedUri = UCrop.getOutput(data)
                if (croppedUri != null) salvarNovaFoto(croppedUri)
            } else if (resultCode == UCrop.RESULT_ERROR && data != null) {
                val erro = UCrop.getError(data)
                Toast.makeText(this, "Não foi possível recortar a foto: ${erro?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun salvarNovaFoto(croppedUri: Uri) {
        val key = plantKey ?: return
        try {
            val dir = File(filesDir, "plant_photos").apply { mkdirs() }
            val novoArquivo = File(dir, "edit_${key}_${System.currentTimeMillis()}.jpg")

            contentResolver.openInputStream(croppedUri)?.use { input ->
                FileOutputStream(novoArquivo).use { output -> input.copyTo(output) }
            }

            // Apaga a foto antiga pra não acumular lixo no armazenamento
            if (currentPhotoPath.isNotBlank()) {
                runCatching { File(currentPhotoPath).delete() }
            }

            currentPhotoPath = novoArquivo.absolutePath
            ivPhoto.setImageURI(Uri.fromFile(novoArquivo))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao salvar a nova foto", Toast.LENGTH_SHORT).show()
        }
    }

    private fun salvarAlteracoes() {
        val uid = Firebase.auth.currentUser?.uid
        val key = plantKey
        if (uid == null || key == null) {
            finish()
            return
        }

        val apelido = etNickname.text.toString().trim().ifBlank { "-" }

        val ref = Firebase.database.getReference("users").child(uid).child("history").child(key)
        ref.child("commonName").setValue(apelido)
        ref.child("photoPath").setValue(currentPhotoPath)

        Toast.makeText(this, "Planta atualizada!", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmarExclusao() {
        AlertDialog.Builder(this)
            .setTitle("Excluir planta")
            .setMessage("Essa identificação vai ser removida do seu histórico. Essa ação não pode ser desfeita.")
            .setPositiveButton("Excluir") { _, _ -> excluirPlanta() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun excluirPlanta() {
        val uid = Firebase.auth.currentUser?.uid
        val key = plantKey
        if (uid == null || key == null) {
            finish()
            return
        }

        Firebase.database.getReference("users").child(uid).child("history").child(key)
            .removeValue()
            .addOnSuccessListener {
                if (currentPhotoPath.isNotBlank()) {
                    runCatching { File(currentPhotoPath).delete() }
                }
                Toast.makeText(this, "Planta excluída", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro ao excluir: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
