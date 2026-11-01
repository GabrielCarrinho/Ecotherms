package com.example.ecotherms

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import kotlin.math.max

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // O app desenha até as bordas e aplica manualmente as áreas seguras.
        // Isso evita que a navbar inferior fique embaixo dos botões/gestos do Android,
        // inclusive em celulares Xiaomi/MIUI, Samsung e aparelhos com navegação por 3 botões.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val backgroundColor = ContextCompat.getColor(this, R.color.texto_claro)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    override fun setContentView(@LayoutRes layoutResID: Int) {
        super.setContentView(layoutResID)
        applySafeAreaInsets()
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        applySafeAreaInsets()
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        applySafeAreaInsets()
    }

    private fun applySafeAreaInsets() {
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return
        val topChild = content.getChildAt(0) ?: return

        // Quando a tela tem menu lateral (DrawerLayout), o conteúdo de verdade
        // é o primeiro filho DELE, não o DrawerLayout em si — senão o respiro
        // do topo não chega até o cabeçalho e ele fica atrás da status bar.
        val root: View = if (topChild is DrawerLayout) {
            topChild.getChildAt(0) ?: topChild
        } else {
            topChild
        }

        val bottomNavBar = root.findViewById<View?>(R.id.bottomNavBar)

        val originalRootPaddingLeft = root.paddingLeft
        val originalRootPaddingTop = root.paddingTop
        val originalRootPaddingRight = root.paddingRight
        val originalRootPaddingBottom = root.paddingBottom

        val originalNavMargins = (bottomNavBar?.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            Margins(it.leftMargin, it.topMargin, it.rightMargin, it.bottomMargin)
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val safeTop = systemBars.top
            val safeBottom = navigationBars.bottom

            view.setPadding(
                originalRootPaddingLeft,
                originalRootPaddingTop + safeTop,
                originalRootPaddingRight,
                if (bottomNavBar == null) originalRootPaddingBottom + safeBottom else originalRootPaddingBottom
            )

            bottomNavBar?.let { nav ->
                val fixedHeight = resources.getDimensionPixelSize(R.dimen.bottom_nav_height)
                val params = nav.layoutParams
                if (params.height != fixedHeight) {
                    params.height = fixedHeight
                }

                val marginParams = params as? ViewGroup.MarginLayoutParams
                if (marginParams != null && originalNavMargins != null) {
                    marginParams.leftMargin = originalNavMargins.left
                    marginParams.topMargin = originalNavMargins.top
                    marginParams.rightMargin = originalNavMargins.right
                    // Espaço real reservado para os botões/gestos do Android.
                    // O max evita ficar colado em aparelhos que retornam inset muito pequeno.
                    marginParams.bottomMargin = originalNavMargins.bottom + max(safeBottom, dp(12))
                    nav.layoutParams = marginParams
                } else {
                    nav.layoutParams = params
                }

                nav.setPadding(
                    nav.paddingLeft,
                    0,
                    nav.paddingRight,
                    0
                )
            }

            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class Margins(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )
}
