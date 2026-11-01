package com.example.ecotherms

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private var navegou = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContentView(R.layout.activity_splash)

        val ivLogo = findViewById<ImageView>(R.id.ivSplashLogo)
        val layoutFrom = findViewById<View>(R.id.layoutSplashFrom)

        // Logo ocupa um percentual da largura da tela
        val larguraTela = resources.displayMetrics.widthPixels
        val tamanhoLogo = (larguraTela * 0.65f).toInt()

        ivLogo.layoutParams = ivLogo.layoutParams.apply {
            width = tamanhoLogo
            height = tamanhoLogo
        }

        // Fade-in escalonado: logo -> "from equipe", igual splash nativo de app grande
        val fadeLogo = ObjectAnimator.ofFloat(ivLogo, "alpha", 0f, 1f).apply {
            duration = 450
        }
        val fadeFrom = ObjectAnimator.ofFloat(layoutFrom, "alpha", 0f, 1f).apply {
            duration = 500
            startDelay = 550
        }

        AnimatorSet().apply {
            playTogether(fadeLogo, fadeFrom)
            start()
        }

        // Splash breve, sem interação — some sozinho depois de um tempo curto
        findViewById<View>(android.R.id.content).postDelayed({
            irParaLogin()
        }, 1800)
    }

    private fun irParaLogin() {
        if (navegou) return
        navegou = true

        val intent = Intent(this, MainActivityLogin::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}