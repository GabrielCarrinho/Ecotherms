package com.example.ecotherms

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ecotherms.databinding.ActivityMainCadastroBinding
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

class MainActivityCadastro : BaseActivity() {

    private lateinit var binding: ActivityMainCadastroBinding
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(resultado.data)
        try {
            val account = task.getResult(ApiException::class.java)
            autenticarComGoogle(account)
        } catch (e: ApiException) {
            Snackbar.make(binding.root, "Não foi possível cadastrar com o Google.", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainCadastroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        configurarGoogleSignIn()

        binding.btnGoogleCadastro.setOnClickListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }

        binding.btnCadastrar.setOnClickListener {
            val nome = binding.editNome.text.toString().trim()
            val email = binding.editEmail.text.toString().trim()
            val senha = binding.editSenha.text.toString().trim()
            val confirmarSenha = binding.editConfirmarSenha.text.toString().trim()

            if (validarCampos(nome, email, senha, confirmarSenha)) {
                cadastrar(nome, email, senha)
            }
        }

        binding.txtVoltarLogin.setOnClickListener {
            finish()
        }

        destacarLinkEntrar()
        configurarIndicadorForcaSenha()
    }

    /** Deixa "Entrar" em verde e negrito, mantendo o resto do texto neutro. */
    private fun destacarLinkEntrar() {
        val textoCompleto = binding.txtVoltarLogin.text.toString()
        val destaque = "Entrar"
        val inicio = textoCompleto.lastIndexOf(destaque)
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
        binding.txtVoltarLogin.text = spannable
    }

    private fun configurarIndicadorForcaSenha() {
        binding.editSenha.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val senha = s?.toString().orEmpty()

                if (senha.isEmpty()) {
                    binding.layoutForcaSenha.visibility = View.INVISIBLE
                    binding.tvForcaSenha.visibility = View.INVISIBLE
                    return
                }

                binding.layoutForcaSenha.visibility = View.VISIBLE
                binding.tvForcaSenha.visibility = View.VISIBLE

                atualizarForcaSenha(calcularForcaSenha(senha))
            }
        })
    }

    /** Como a senha aqui é só numérica, a "força" é medida pelo comprimento. */
    private fun calcularForcaSenha(senha: String): Int {
        return when {
            senha.length < 6 -> 1  // fraca
            senha.length < 8 -> 2  // média
            senha.length < 10 -> 3 // boa
            else -> 4              // forte
        }
    }

    private fun atualizarForcaSenha(nivel: Int) {
        val barras = listOf(binding.barForca1, binding.barForca2, binding.barForca3, binding.barForca4)

        val corAtiva = when (nivel) {
            1 -> R.drawable.bg_bar_forca_fraca
            2 -> R.drawable.bg_bar_forca_media
            3 -> R.drawable.bg_bar_forca_media
            else -> R.drawable.bg_bar_forca_forte
        }

        barras.forEachIndexed { index, barra ->
            barra.setBackgroundResource(if (index < nivel) corAtiva else R.drawable.bg_bar_forca_inativa)
        }

        val (texto, cor) = when (nivel) {
            1 -> "Senha fraca — use pelo menos 6 números" to R.color.erro
            2 -> "Senha média" to R.color.texto_secundario
            3 -> "Senha boa" to R.color.verde_medio
            else -> "Senha forte" to R.color.verde_medio
        }
        binding.tvForcaSenha.text = texto
        binding.tvForcaSenha.setTextColor(ContextCompat.getColor(this, cor))
    }

    private fun validarCampos(
        nome: String,
        email: String,
        senha: String,
        confirmarSenha: String
    ): Boolean {
        var valido = true

        if (nome.isEmpty()) {
            binding.txtInputLayoutNome.error = "Informe seu nome"
            valido = false
        } else {
            binding.txtInputLayoutNome.error = null
        }

        if (email.isEmpty()) {
            binding.txtInputLayoutEmail.error = "Informe seu e-mail"
            valido = false
        } else if (!email.contains("@")) {
            binding.txtInputLayoutEmail.error = "E-mail inválido"
            valido = false
        } else {
            binding.txtInputLayoutEmail.error = null
        }

        if (senha.isEmpty()) {
            binding.txtInputLayoutSenha.error = "Informe uma senha"
            valido = false
        } else if (senha.length < 6) {
            binding.txtInputLayoutSenha.error = "A senha deve ter ao menos 6 caracteres"
            valido = false
        } else {
            binding.txtInputLayoutSenha.error = null
        }

        if (confirmarSenha.isEmpty()) {
            binding.txtInputLayoutConfirmarSenha.error = "Confirme sua senha"
            valido = false
        } else if (senha != confirmarSenha) {
            binding.txtInputLayoutConfirmarSenha.error = "As senhas não coincidem"
            valido = false
        } else {
            binding.txtInputLayoutConfirmarSenha.error = null
        }

        return valido
    }

    // ─── Cadastro com Google ─────────────────────────────────────────────────

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
                    val profileRef = Firebase.database.getReference("users").child(user.uid).child("profile")
                    profileRef.child("nome").setValue(user.displayName ?: account.displayName ?: "")
                    profileRef.child("email").setValue(user.email ?: account.email ?: "")

                    // Foto de perfil do Google (telefone não é solicitado pela conta Google, fica em branco)
                    salvarFotoDoGoogle(user.uid, account.photoUrl?.toString())
                }

                Snackbar.make(binding.root, "Cadastro realizado com sucesso!", Snackbar.LENGTH_SHORT).show()

                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Snackbar.make(binding.root, "Erro ao cadastrar com Google: ${e.message}", Snackbar.LENGTH_LONG).show()
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

    private fun cadastrar(nome: String, email: String, senha: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnCadastrar.isEnabled = false

        Firebase.auth.createUserWithEmailAndPassword(email, senha)
            .addOnSuccessListener { resultado ->
                val uid = resultado.user?.uid
                if (uid == null) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCadastrar.isEnabled = true
                    Snackbar.make(binding.root, "Erro inesperado ao criar usuário", Snackbar.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                // Salva nome e e-mail no perfil do usuário no Realtime Database (chave = uid)
                val profileRef = Firebase.database.getReference("users").child(uid).child("profile")
                profileRef.child("nome").setValue(nome)
                profileRef.child("email").setValue(email)

                binding.progressBar.visibility = View.GONE
                Snackbar.make(binding.root, "Cadastro realizado com sucesso!", Snackbar.LENGTH_SHORT).show()

                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnCadastrar.isEnabled = true

                when (e) {
                    is com.google.firebase.auth.FirebaseAuthUserCollisionException ->
                        binding.txtInputLayoutEmail.error = "Este e-mail já está cadastrado"
                    is com.google.firebase.auth.FirebaseAuthWeakPasswordException ->
                        binding.txtInputLayoutSenha.error = "Senha muito fraca"
                    is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ->
                        binding.txtInputLayoutEmail.error = "E-mail inválido"
                    else ->
                        Snackbar.make(binding.root, "Erro: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
    }
}