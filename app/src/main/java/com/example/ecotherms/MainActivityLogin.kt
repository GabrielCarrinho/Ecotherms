package com.example.ecotherms

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ecotherms.databinding.ActivityMainLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.database.database
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivityLogin : BaseActivity() {

    private lateinit var binding: ActivityMainLoginBinding
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(resultado.data)
        try {
            val account = task.getResult(ApiException::class.java)
            autenticarComGoogle(account)
        } catch (e: ApiException) {
            Snackbar.make(binding.root, "Não foi possível entrar com o Google.", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Se já existe uma sessão válida do Firebase Auth, pula direto pra Home
        if (Firebase.auth.currentUser != null) {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        binding = ActivityMainLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        configurarGoogleSignIn()

        binding.btnGoogleLogin.setOnClickListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }

        binding.btnEntrar.setOnClickListener {
            val email = binding.editEmail.text.toString().trim()
            val senha = binding.editSenha.text.toString().trim()

            when {
                email.isEmpty() -> {
                    binding.txtInputLayoutEmail.error = "Preencha o E-mail!"
                }
                senha.isEmpty() -> {
                    binding.txtInputLayoutSenha.error = "Preencha a Senha!"
                }
                !email.contains("@") -> {
                    binding.txtInputLayoutEmail.error = "E-mail inválido!"
                }
                senha.length < 6 -> {
                    binding.txtInputLayoutSenha.error = "A senha precisa ter pelo menos 6 caracteres!"
                }
                else -> {
                    binding.txtInputLayoutEmail.error = null
                    binding.txtInputLayoutSenha.error = null
                    login(email, senha)
                }
            }
        }

        binding.txtTelaCadastro.setOnClickListener {
            startActivity(Intent(this, MainActivityCadastro::class.java))
        }

        destacarLinkCadastro()

        binding.txtEsqueciSenha.setOnClickListener {
            abrirDialogEsqueciSenha()
        }
    }

    /** Deixa "Cadastre-se!" em verde e negrito, mantendo o resto do texto neutro. */
    private fun destacarLinkCadastro() {
        val textoCompleto = binding.txtTelaCadastro.text.toString()
        val destaque = "Cadastre-se!"
        val inicio = textoCompleto.indexOf(destaque)
        if (inicio == -1) return

        val spannable = SpannableString(textoCompleto)
        spannable.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.verde_medio)),
            inicio, inicio + destaque.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            StyleSpan(Typeface.BOLD),
            inicio, inicio + destaque.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.txtTelaCadastro.text = spannable
    }

    private fun abrirDialogEsqueciSenha() {
        val campoEmail = EditText(this).apply {
            hint = "Seu e-mail cadastrado"
            setText(binding.editEmail.text.toString().trim())
        }

        AlertDialog.Builder(this)
            .setTitle("Redefinir senha")
            .setMessage("Vamos te enviar um e-mail com um link pra você criar uma nova senha.")
            .setView(campoEmail)
            .setPositiveButton("Enviar") { _, _ ->
                val email = campoEmail.text.toString().trim()
                if (email.isBlank() || !email.contains("@")) {
                    Snackbar.make(binding.root, "Digite um e-mail válido", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                enviarEmailRedefinicao(email)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun enviarEmailRedefinicao(email: String) {
        Firebase.auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Snackbar.make(binding.root, "E-mail de redefinição enviado! Confira sua caixa de entrada.", Snackbar.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Snackbar.make(binding.root, "Erro ao enviar: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
    }

    // ─── Login com Google ────────────────────────────────────────────────────

    private fun configurarGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun autenticarComGoogle(account: GoogleSignInAccount) {
        val idToken = account.idToken
        if (idToken == null) {
            Snackbar.make(binding.root, "Erro ao obter credenciais do Google.", Snackbar.LENGTH_LONG).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        Firebase.auth.signInWithCredential(credential)
            .addOnSuccessListener { resultado ->
                binding.progressBar.visibility = View.GONE

                val user = resultado.user
                if (resultado.additionalUserInfo?.isNewUser == true && user != null) {
                    // Primeiro acesso via Google: salva nome e e-mail no perfil, igual ao cadastro normal
                    val profileRef = Firebase.database.getReference("users").child(user.uid).child("profile")
                    profileRef.child("nome").setValue(user.displayName ?: account.displayName ?: "")
                    profileRef.child("email").setValue(user.email ?: account.email ?: "")

                    // Foto de perfil do Google (telefone não é solicitado pela conta Google, fica em branco)
                    salvarFotoDoGoogle(user.uid, account.photoUrl?.toString())
                }

                Snackbar.make(binding.root, "Login efetuado com sucesso!", Snackbar.LENGTH_SHORT).show()

                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Snackbar.make(binding.root, "Erro ao entrar com Google: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
    }

    /**
     * Baixa a foto de perfil do Google e salva no mesmo formato/local que o app já usa
     * pra fotos de perfil (arquivo em filesDir + caminho no SharedPreferences), pra
     * integrar direto com a tela de Perfil sem precisar mudar nada lá.
     */
    private fun salvarFotoDoGoogle(uid: String, photoUrl: String?) {
        if (photoUrl == null) return

        Thread {
            try {
                val conexao = URL(photoUrl).openConnection() as HttpURLConnection
                conexao.doInput = true
                conexao.connect()

                val arquivo = File(filesDir, "profile_photo_$uid.jpg")
                conexao.inputStream.use { entrada ->
                    FileOutputStream(arquivo).use { saida ->
                        entrada.copyTo(saida)
                    }
                }

                getSharedPreferences("user_profile", MODE_PRIVATE)
                    .edit()
                    .putString("photo_path_$uid", arquivo.absolutePath)
                    .apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun login(email: String, senha: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnEntrar.isEnabled = false

        Firebase.auth.signInWithEmailAndPassword(email, senha)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                binding.btnEntrar.isEnabled = true

                Snackbar.make(binding.root, "Login efetuado com sucesso!", Snackbar.LENGTH_SHORT).show()

                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnEntrar.isEnabled = true

                when (e) {
                    is com.google.firebase.auth.FirebaseAuthInvalidUserException ->
                        binding.txtInputLayoutEmail.error = "Não há cadastro para este endereço de email"
                    is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ->
                        binding.txtInputLayoutSenha.error = "Senha incorreta!"
                    else ->
                        Snackbar.make(binding.root, "Erro: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
    }
}