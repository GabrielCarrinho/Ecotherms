package com.example.ecotherms

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import java.io.File

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        exibirVersaoDoApp()
        configurarNavegacao()
        configurarBottomNav()
    }

    override fun onResume() {
        super.onResume()

        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            // Sessão perdida por algum motivo: manda de volta pro login
            irParaLogin()
            return
        }

        carregarSaudacao(uid)
        carregarAvatar(uid)
    }

    // ─── Configuração inicial da tela ───────────────────────────────────────

    private fun exibirVersaoDoApp() {
        val versionName = obterVersionNameDoApp()
        val tvVersion = findViewById<TextView>(R.id.tv_version)
        tvVersion.text = "Versão: $versionName"
    }

    private fun obterVersionNameDoApp(): String? {
        val suportaApiDeFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        if (suportaApiDeFlags) {
            val flags = PackageManager.PackageInfoFlags.of(0)
            return packageManager.getPackageInfo(packageName, flags).versionName
        }

        @Suppress("DEPRECATION")
        return packageManager.getPackageInfo(packageName, 0).versionName
    }

    private fun configurarNavegacao() {
        // Perfil: card, botão editar e botão "Ver meu perfil" levam todos pro mesmo lugar
        configurarClique(R.id.card_profile) { irParaPerfil() }
        configurarClique(R.id.btn_edit_profile) { irParaPerfil() }
        configurarClique(R.id.btn_ver_perfil) { irParaPerfil() }

        configurarClique(R.id.card_prefs) { irPara(PreferencesActivity::class.java) }
        configurarClique(R.id.card_history) { irPara(PlantHistoryActivity::class.java) }

        configurarClique(R.id.card_logout) { fazerLogout() }
        configurarClique(R.id.card_delete_account) { confirmarExclusaoConta() }
    }

    private fun configurarClique(idDaView: Int, aoClicar: () -> Unit) {
        val view = findViewById<View>(idDaView)
        view.setOnClickListener { aoClicar() }
    }

    private fun irParaPerfil() {
        irPara(ProfileActivity::class.java)
    }

    private fun irPara(activity: Class<*>) {
        val intent = Intent(this, activity)
        startActivity(intent)
    }

    /** Barra de navegação inferior, igual às outras telas principais — aqui já estamos na aba "Perfil". */
    private fun configurarBottomNav() {
        val navHome = findViewById<LinearLayout>(R.id.navHome)
        val navChart = findViewById<LinearLayout>(R.id.navChart)
        val navPlants = findViewById<LinearLayout>(R.id.navPlants)
        val navProfile = findViewById<LinearLayout>(R.id.navProfile)

        navHome.setOnClickListener { irParaComoAbaPrincipal(MainActivity::class.java) }
        navChart.setOnClickListener { irParaComoAbaPrincipal(MainActivityDados::class.java) }
        navPlants.setOnClickListener { irParaComoAbaPrincipal(PlantScannerActivity::class.java) }
        navProfile.setOnClickListener { /* já estamos nessa aba */ }
    }

    private fun irParaComoAbaPrincipal(activity: Class<*>) {
        val intent = Intent(this, activity)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    // ─── Saudação e avatar ───────────────────────────────────────────────────

    private fun carregarSaudacao(uid: String) {
        val tvGreeting = findViewById<TextView>(R.id.tv_greeting)

        Firebase.database.getReference("users").child(uid).child("profile").child("nome")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val name = snapshot.getValue(String::class.java)?.trim().orEmpty()
                    tvGreeting.text = if (name.isNotEmpty()) "Olá, $name" else "Olá"
                }

                override fun onCancelled(error: DatabaseError) {
                    tvGreeting.text = "Olá"
                }
            })
    }

    private fun carregarAvatar(uid: String) {
        val ivAvatar = findViewById<ImageView>(R.id.iv_avatar)
        val ivPlaceholder = findViewById<ImageView>(R.id.iv_avatar_placeholder)

        val bitmap = carregarBitmapDaFotoSalva(uid)

        if (bitmap != null) {
            ivAvatar.setImageBitmap(bitmap)
            ivPlaceholder.visibility = View.GONE
        } else {
            ivAvatar.setImageDrawable(null)
            ivPlaceholder.visibility = View.VISIBLE
        }
    }

    private fun carregarBitmapDaFotoSalva(uid: String): android.graphics.Bitmap? {
        val prefs = getSharedPreferences("user_profile", MODE_PRIVATE)
        val caminhoDaFoto = prefs.getString("photo_path_$uid", null) ?: return null

        val arquivo = File(caminhoDaFoto)
        if (!arquivo.exists()) return null

        return BitmapFactory.decodeFile(arquivo.absolutePath)
    }

    // ─── Sair da conta ───────────────────────────────────────────────────────

    private fun fazerLogout() {
        Firebase.auth.signOut()
        irParaLogin()
    }

    private fun irParaLogin() {
        val intent = Intent(this, MainActivityLogin::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // ─── Excluir conta ───────────────────────────────────────────────────────

    private fun confirmarExclusaoConta() {
        AlertDialog.Builder(this)
            .setTitle("Excluir conta")
            .setMessage(
                "Essa ação irá apagar permanentemente sua conta, incluindo seu perfil e todo o " +
                "histórico de plantas salvo. Essa ação não poderá ser desfeita.\n\nDeseja continuar?"
            )
            .setPositiveButton("Excluir") { _, _ -> pedirSenhaEExcluir() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Por segurança, o Firebase exige uma sessão "recente" para excluir a conta.
     * Em vez de deixar isso falhar depois de já ter apagado os dados, confirmamos a
     * senha primeiro (reautenticação) — só apagamos algo se essa confirmação passar.
     */
    private fun pedirSenhaEExcluir() {
        val user = Firebase.auth.currentUser
        val email = user?.email
        if (user == null || email == null) {
            irParaLogin()
            return
        }

        val campoSenha = criarCampoDeSenha()
        val dialogo = criarDialogoDeConfirmacaoDeSenha(campoSenha)

        configurarBotaoConfirmar(dialogo, campoSenha) { senha ->
            reautenticarEExcluir(user, email, senha)
        }
    }

    private fun criarCampoDeSenha(): EditText {
        val campo = EditText(this)
        campo.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        campo.hint = "Sua senha atual"
        return campo
    }

    private fun criarDialogoDeConfirmacaoDeSenha(campoSenha: EditText): AlertDialog {
        return AlertDialog.Builder(this)
            .setTitle("Confirme sua senha")
            .setMessage("Por segurança, digite sua senha para confirmar a exclusão da conta.")
            .setView(campoSenha)
            .setPositiveButton("Confirmar", null) // listener customizado abaixo, pra não fechar em senha vazia
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun configurarBotaoConfirmar(dialogo: AlertDialog, campoSenha: EditText, aoConfirmar: (String) -> Unit) {
        val botaoConfirmar = dialogo.getButton(AlertDialog.BUTTON_POSITIVE)

        botaoConfirmar.setOnClickListener {
            val senha = campoSenha.text.toString()
            if (senha.isBlank()) {
                campoSenha.error = "Digite sua senha"
                return@setOnClickListener
            }
            dialogo.dismiss()
            aoConfirmar(senha)
        }
    }

    private fun reautenticarEExcluir(user: FirebaseUser, email: String, senha: String) {
        val credential = EmailAuthProvider.getCredential(email, senha)

        user.reauthenticate(credential)
            .addOnSuccessListener { excluirConta() }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Senha incorreta ou erro ao confirmar: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun excluirConta() {
        val user = Firebase.auth.currentUser
        val uid = user?.uid
        if (user == null || uid == null) {
            irParaLogin()
            return
        }

        // As regras de segurança exigem auth.uid == $uid, então apagamos os dados
        // ANTES de apagar a conta em si — depois disso a sessão deixa de valer.
        Firebase.database.getReference("users").child(uid).removeValue()
            .addOnCompleteListener {
                apagarFotoLocal(uid)
                apagarContaNoFirebaseAuth(user)
            }
    }

    private fun apagarFotoLocal(uid: String) {
        val prefs = getSharedPreferences("user_profile", MODE_PRIVATE)

        prefs.getString("photo_path_$uid", null)?.let { caminho ->
            File(caminho).delete()
        }

        prefs.edit().remove("photo_path_$uid").apply()
    }

    private fun apagarContaNoFirebaseAuth(user: FirebaseUser) {
        user.delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Conta excluída com sucesso!", Toast.LENGTH_SHORT).show()
                irParaLogin()
            }
            .addOnFailureListener { e -> mostrarErroDeExclusao(e) }
    }

    private fun mostrarErroDeExclusao(erro: Exception) {
        val mensagem = if (erro is FirebaseAuthRecentLoginRequiredException) {
            "Por segurança, saia e faça login novamente antes de excluir a conta."
        } else {
            "Erro ao excluir conta: ${erro.message}"
        }
        Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show()
    }
}
