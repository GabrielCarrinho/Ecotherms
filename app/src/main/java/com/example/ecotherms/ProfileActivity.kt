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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream

class ProfileActivity : BaseActivity() {

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var ivAvatar: ImageView
    private lateinit var ivAvatarPlaceholder: ImageView

    private var userKey: String? = null
    private lateinit var profileRef: DatabaseReference

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        val uid = userKey ?: return@registerForActivityResult
        iniciarRecorte(uri, uid)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        etName   = findViewById(R.id.et_name)
        etEmail  = findViewById(R.id.et_email)
        etPhone  = findViewById(R.id.et_phone)
        ivAvatar = findViewById(R.id.iv_avatar)
        ivAvatarPlaceholder = findViewById(R.id.iv_avatar_placeholder)

        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Sessão não encontrada", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        userKey = uid
        profileRef = Firebase.database.getReference("users").child(uid).child("profile")

        carregarPerfil(uid)

        findViewById<ImageButton>(R.id.btn_change_photo).setOnClickListener {
            pickPhoto.launch("image/*")
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btn_save_profile).setOnClickListener {
            salvarPerfil()
        }
    }

    private fun carregarPerfil(uid: String) {
        // Preenche o e-mail imediatamente com o da conta autenticada (não muda)
        etEmail.setText(Firebase.auth.currentUser?.email.orEmpty())

        // Nome e telefone vêm do Realtime Database, específicos deste usuário
        profileRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nome = snapshot.child("nome").getValue(String::class.java).orEmpty()
                val telefone = snapshot.child("telefone").getValue(String::class.java).orEmpty()
                etName.setText(nome)
                etPhone.setText(telefone)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ProfileActivity, "Erro ao carregar perfil: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Foto fica local, mas separada por usuário (uid)
        mostrarFotoSalva(uid)
    }

    private fun mostrarFotoSalva(uid: String) {
        val prefs = getSharedPreferences("user_profile", MODE_PRIVATE)
        val path = prefs.getString("photo_path_$uid", null)
        if (path != null) {
            val file = File(path)
            if (file.exists() && carregarBitmapNoAvatar(file)) {
                ivAvatarPlaceholder.visibility = android.view.View.GONE
                return
            }
        }
        // Sem foto: mantém o avatar transparente e mostra só o ícone minimalista
        ivAvatar.setImageDrawable(null)
        ivAvatarPlaceholder.visibility = android.view.View.VISIBLE
    }

    /**
     * Decodifica o arquivo direto pra Bitmap, em vez de usar setImageURI.
     * O ImageView cacheia por identidade do Uri — como sempre salvamos no MESMO
     * arquivo (profile_photo_$uid.jpg), o Android às vezes não percebia que o
     * conteúdo mudou e continuava mostrando a foto antiga. Decodificar manualmente
     * evita esse cache e garante que a imagem mais recente sempre apareça.
     */
    private fun carregarBitmapNoAvatar(file: File): Boolean {
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return false
        ivAvatar.setImageBitmap(bitmap)
        return true
    }

    private fun iniciarRecorte(sourceUri: Uri, uid: String) {
        val destinoArquivo = File(cacheDir, "cropped_$uid.jpg")
        val destinoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", destinoArquivo)

        val opcoes = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(92)
            setToolbarTitle("Ajustar foto")
            setToolbarColor(ContextCompat.getColor(this@ProfileActivity, R.color.botao))
            setStatusBarColor(ContextCompat.getColor(this@ProfileActivity, R.color.botao))
            setActiveControlsWidgetColor(ContextCompat.getColor(this@ProfileActivity, R.color.botao))
            setToolbarWidgetColor(ContextCompat.getColor(this@ProfileActivity, android.R.color.white))
            setRootViewBackgroundColor(ContextCompat.getColor(this@ProfileActivity, R.color.verde_escuro))
            setDimmedLayerColor(ContextCompat.getColor(this@ProfileActivity, R.color.verde_escuro))
            setCircleDimmedLayer(true) // recorte circular, já que o avatar é redondo
            setShowCropFrame(false)
            setShowCropGrid(false)
            setHideBottomControls(false)
        }

        UCrop.of(sourceUri, destinoUri)
            .withAspectRatio(1f, 1f)
            .withOptions(opcoes)
            .start(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == UCrop.REQUEST_CROP) {
            val uid = userKey ?: return
            if (resultCode == Activity.RESULT_OK && data != null) {
                val croppedUri = UCrop.getOutput(data)
                if (croppedUri != null) {
                    salvarFotoRecortada(croppedUri, uid)
                }
            } else if (resultCode == UCrop.RESULT_ERROR && data != null) {
                val erro = UCrop.getError(data)
                Toast.makeText(this, "Não foi possível recortar a foto: ${erro?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun salvarFotoRecortada(croppedUri: Uri, uid: String) {
        try {
            val inputStream = contentResolver.openInputStream(croppedUri) ?: return
            val file = File(filesDir, "profile_photo_$uid.jpg")
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

            getSharedPreferences("user_profile", MODE_PRIVATE)
                .edit()
                .putString("photo_path_$uid", file.absolutePath)
                .apply()

            ivAvatarPlaceholder.visibility = android.view.View.GONE
            carregarBitmapNoAvatar(file)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao salvar foto", Toast.LENGTH_SHORT).show()
        }
    }

    private fun salvarPerfil() {
        val nome = etName.text.toString().trim()
        val telefone = etPhone.text.toString().trim()

        profileRef.child("nome").setValue(nome)
        profileRef.child("telefone").setValue(telefone)
        profileRef.child("email").setValue(Firebase.auth.currentUser?.email.orEmpty())

        Toast.makeText(this, "Perfil salvo!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
