package com.example.ecotherms


import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : BaseActivity() {

    // ─── Views ────────────────────────────────────────────────────────────────
    private lateinit var tvNomeEstufa           : TextView
    private lateinit var tvUltimaAtualizacao    : TextView
    private lateinit var tvStatusConexao        : TextView
    private lateinit var layoutCrachaConexao    : android.widget.LinearLayout

    private lateinit var tvValorTemperatura     : TextView
    private lateinit var tvStatusTemperatura    : TextView
    private lateinit var progressTemperatura    : ProgressBar

    private lateinit var tvValorUmidade         : TextView
    private lateinit var tvStatusUmidade        : TextView
    private lateinit var progressUmidade        : ProgressBar

    private lateinit var tvValorUmidadeSolo     : TextView
    private lateinit var tvStatusUmidadeSolo    : TextView
    private lateinit var progressUmidadeSolo    : ProgressBar

    private lateinit var tvValorTemperaturaSolo : TextView
    private lateinit var tvStatusTemperaturaSolo: TextView
    private lateinit var progressTemperaturaSolo: ProgressBar

    private lateinit var tvValorIrrigacao       : TextView
    private lateinit var tvStatusIrrigacao      : TextView
    private lateinit var progressIrrigacao      : ProgressBar

    private lateinit var tvStatusAmbiente       : TextView
    private lateinit var ivAlertaTemperatura    : ImageView
    private lateinit var ivAlertaUmidade        : ImageView
    private lateinit var ivAlertaUmidadeSolo    : ImageView
    private lateinit var ivAlertaTemperaturaSolo: ImageView
    private lateinit var ivAlertaIrrigacao      : ImageView
    private lateinit var tvStatusCiclo          : TextView

    private lateinit var btnAtualizar           : Button

    private lateinit var navHome                : LinearLayout
    private lateinit var navChart               : LinearLayout
    private lateinit var navPlants              : LinearLayout
    private lateinit var navProfile             : LinearLayout

    private lateinit var drawerLayout           : DrawerLayout
    private lateinit var btnMenu                : ImageButton
    private lateinit var btnNotifications       : ImageButton

    //dados em Cº
    private var temperaturaAtual        : Float = 28f
    private var umidadeAtual            : Float = 65f
    private var umidadeSoloAtual        : Float = 45f
    private var temperaturaSoloAtual    : Float = 22f
    private var irrigacaoAtual          : Float = 40f

    // salvar a unidade
    private var usarFahrenheit: Boolean = false

    // ─── Faixas ideais por métrica (fonte única usada tanto pelas cores quanto pelos alertas) ──
    private val faixaIdealTemperatura      = 16f..35f
    private val faixaIdealUmidade          = 31f..70f
    private val faixaIdealUmidadeSolo      = 26f..75f
    private val faixaIdealTemperaturaSolo  = 10f..29f
    private val faixaIdealIrrigacao        = 21f..85f

    // recebe a preferencia do usuario
    private val receptorUnidadeTemp = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PreferencesActivity.ACTION_TEMP_UNIT_CHANGED) {
                usarFahrenheit = intent.getBooleanExtra(
                    PreferencesActivity.EXTRA_USE_FAHRENHEIT, false
                )
                atualizarInterface()
            }
        }
    }

    private val manipulador = Handler(Looper.getMainLooper())
    private val tarefaAtualizacao = object : Runnable {
        override fun run() {
            atualizarInterface()
            manipulador.postDelayed(this, 10_000L)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // teste do firebase (inicio)
        android.util.Log.d(
            "FirebaseTeste",
            "Apps Firebase: ${com.google.firebase.FirebaseApp.getApps(this).size}"
        )

        // teste de escrever no realtime database
        setContentView(R.layout.activity_main)

        // verifica e salva a preferencia de temperatura
        usarFahrenheit = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getBoolean("fahrenheit", false)

        val bancoDados = Firebase.database
        val referenciaEstufas = bancoDados.getReference("estufas")

        vincularViews()
        aplicarFormasProgramaticas()
        configurarListeners()
        atualizarInterface()

        referenciaEstufas.child("estufa1")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    temperaturaAtual     = snapshot.lerFloatSeguro("temperatura")
                    umidadeAtual         = snapshot.lerFloatSeguro("umidade")
                    umidadeSoloAtual     = snapshot.lerFloatSeguro("umidadeSolo")
                    temperaturaSoloAtual = snapshot.lerFloatSeguro("temperaturaSolo")
                    irrigacaoAtual       = snapshot.lerFloatSeguro("irrigacao")
                    atualizarInterface()
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e(
                        "FirebaseTeste",
                        "Erro ao ler dados da estufa",
                        error.toException()
                    )
                }
            })
    }

    override fun onResume() {
        super.onResume()

        // recebe
        val filtro = IntentFilter(PreferencesActivity.ACTION_TEMP_UNIT_CHANGED)
        ContextCompat.registerReceiver(
            this,
            receptorUnidadeTemp,
            filtro,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // verificacao de preferencia novamente
        usarFahrenheit = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getBoolean("fahrenheit", false)
        atualizarInterface()
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(receptorUnidadeTemp) }
    }

    override fun onDestroy() {
        super.onDestroy()
        manipulador.removeCallbacks(tarefaAtualizacao)
    }

    private fun DataSnapshot.lerFloatSeguro(campo: String): Float {
        val valor = child(campo).value ?: return 0f
        return when (valor) {
            is Number -> valor.toFloat()
            is String -> valor.replace(',', '.').toFloatOrNull() ?: 0f
            is Boolean -> if (valor) 1f else 0f
            else -> 0f
        }
    }

    // converte para fah
    private fun Float.toDisplayTemp(): String {
        return if (usarFahrenheit) {
            val f = this * 9f / 5f + 32f
            "${"%.1f".format(f)}°F"
        } else {
            "${"%.1f".format(this)}°C"
        }
    }


    private fun vincularViews() {
        tvNomeEstufa            = findViewById(R.id.tvGreenhouseName)
        tvUltimaAtualizacao     = findViewById(R.id.tvLastUpdate)
        tvStatusConexao         = findViewById(R.id.tvConnectionStatus)
        layoutCrachaConexao     = findViewById(R.id.layoutConnectionBadge)

        tvValorTemperatura      = findViewById(R.id.tvTemperatureValue)
        tvStatusTemperatura     = findViewById(R.id.tvTemperatureStatus)
        progressTemperatura     = findViewById(R.id.progressTemperature)

        tvValorUmidade          = findViewById(R.id.tvHumidityValue)
        tvStatusUmidade         = findViewById(R.id.tvHumidityStatus)
        progressUmidade         = findViewById(R.id.progressHumidity)

        tvValorUmidadeSolo      = findViewById(R.id.tvSoilHumidityValue)
        tvStatusUmidadeSolo     = findViewById(R.id.tvSoilHumidityStatus)
        progressUmidadeSolo     = findViewById(R.id.progressSoilHumidity)

        tvValorTemperaturaSolo  = findViewById(R.id.tvSoilTemperatureValue)
        tvStatusTemperaturaSolo = findViewById(R.id.tvSoilTemperatureStatus)
        progressTemperaturaSolo = findViewById(R.id.progressSoilTemperature)

        tvValorIrrigacao        = findViewById(R.id.tvIrrigationValue)
        tvStatusIrrigacao       = findViewById(R.id.tvIrrigationStatus)
        progressIrrigacao       = findViewById(R.id.progressIrrigation)

        tvStatusAmbiente        = findViewById(R.id.tvEnvironmentStatus)
        ivAlertaTemperatura     = findViewById(R.id.ivTemperatureAlert)
        ivAlertaUmidade         = findViewById(R.id.ivHumidityAlert)
        ivAlertaUmidadeSolo     = findViewById(R.id.ivSoilHumidityAlert)
        ivAlertaTemperaturaSolo = findViewById(R.id.ivSoilTemperatureAlert)
        ivAlertaIrrigacao       = findViewById(R.id.ivIrrigationAlert)
        tvStatusCiclo           = findViewById(R.id.tvCycleStatus)

        btnAtualizar            = findViewById(R.id.btnRefresh)

        navHome                 = findViewById(R.id.navHome)
        navChart                = findViewById(R.id.navChart)
        navPlants               = findViewById(R.id.navPlants)
        navProfile              = findViewById(R.id.navProfile)

        drawerLayout            = findViewById(R.id.drawerLayout)
        btnMenu                 = findViewById(R.id.btnMenu)
        btnNotifications        = findViewById(R.id.btnNotifications)

        tvNomeEstufa.text = "Estufa casa de plantas"
    }

    // ─── Formas programáticas ─────────────────────────────────────────────────
    private fun aplicarFormasProgramaticas() {
        val fundoCracha = GradientDrawable().apply {
            shape        = GradientDrawable.RECTANGLE
            cornerRadius = 40f
            setColor(Color.parseColor("#1E3A29"))
            setStroke(2, Color.parseColor("#A8D5B2"))
        }
        layoutCrachaConexao.background = fundoCracha
        tvStatusConexao.setTextColor(Color.parseColor("#FFFFFF"))

        colorirBarraProgresso(progressTemperatura,     Color.parseColor("#D5DBD1"))
        colorirBarraProgresso(progressUmidade,         Color.parseColor("#D5DBD1"))
        colorirBarraProgresso(progressUmidadeSolo,     Color.parseColor("#D5DBD1"))
        colorirBarraProgresso(progressTemperaturaSolo, Color.parseColor("#D5DBD1"))
        colorirBarraProgresso(progressIrrigacao,       Color.parseColor("#D5DBD1"))
    }

    private fun colorirBarraProgresso(barra: ProgressBar, corPreenchimento: Int) {
        val fundoTrilho = GradientDrawable().apply {
            shape        = GradientDrawable.RECTANGLE
            cornerRadius = 8f
            setColor(Color.parseColor("#000000"))
        }
        val preenchimento = GradientDrawable().apply {
            shape        = GradientDrawable.RECTANGLE
            cornerRadius = 8f
            setColor(corPreenchimento)
        }

        // Um GradientDrawable puro sempre se desenha 100% cheio, ignorando o valor do progresso.
        // O ClipDrawable é quem realmente "corta" o preenchimento de acordo com o progresso atual.
        val preenchimentoRecortado = ClipDrawable(preenchimento, Gravity.START, ClipDrawable.HORIZONTAL)

        val camadas = LayerDrawable(arrayOf(fundoTrilho, preenchimentoRecortado)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
        barra.progressDrawable = camadas
    }

    // ─── Listeners ────────────────────────────────────────────────────────────
    private fun configurarListeners() {
        btnAtualizar.setOnClickListener { atualizarInterface() }

        navHome.setOnClickListener { }

        navChart.setOnClickListener {
            val intencao = Intent(this, MainActivityDados::class.java)
            intencao.putExtra("greenhouse_name",  tvNomeEstufa.text.toString())
            intencao.putExtra("temperature",      temperaturaAtual)
            intencao.putExtra("humidity",         umidadeAtual)
            intencao.putExtra("soil_humidity",    umidadeSoloAtual)
            intencao.putExtra("soil_temperature", temperaturaSoloAtual)
            intencao.putExtra("irrigation",       irrigacaoAtual)
            startActivity(intencao)
        }

        navPlants.setOnClickListener {
            startActivity(
                Intent(this, PlantScannerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }

        navProfile.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        btnNotifications.setOnClickListener {
            Toast.makeText(this, "Nenhuma notificação por enquanto", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.drawerItemHistory).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, PlantHistoryActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.drawerItemProfile).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.drawerItemPreferences).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, PreferencesActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.drawerItemSettings).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.drawerItemLogout).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            Firebase.auth.signOut()
            val intent = Intent(this, MainActivityLogin::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // ─── Atualização da UI ────────────────────────────────────────────────────
    private fun atualizarInterface() {
        atualizarMarcaTempo()
        atualizarTemperatura()
        atualizarUmidade()
        atualizarUmidadeSolo()
        atualizarTemperaturaSolo()
        atualizarIrrigacao()
        atualizarCartaoAmbiente()
        atualizarCartaoCiclo()
    }

    private fun atualizarMarcaTempo() {
        val formatoData = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        tvUltimaAtualizacao.text = "Última atualização: ${formatoData.format(Date())}"
    }

    private fun atualizarTemperatura() {
        val temp = temperaturaAtual
        tvValorTemperatura.text = temp.toDisplayTemp()
        val cor = corPorFaixaIdeal(temp, faixaIdealTemperatura)
        animarProgresso(progressTemperatura, ((temp / 90f) * 100).toInt(), cor)

        tvStatusTemperatura.text = when {
            temp < 16f -> "Frio"
            temp < 36f -> "Normal"
            temp < 56f -> "Quente"
            else       -> "Crítico"
        }
    }

    private fun atualizarUmidade() {
        val umid = umidadeAtual
        tvValorUmidade.text = "${"%.1f".format(umid)}%"
        val cor = corPorFaixaIdeal(umid, faixaIdealUmidade)
        animarProgresso(progressUmidade, umid.toInt(), cor)

        tvStatusUmidade.text = when {
            umid <= 30f -> "Seco"
            umid <= 70f -> "Ideal"
            else        -> "Úmido"
        }
    }

    private fun atualizarUmidadeSolo() {
        val umidSolo = umidadeSoloAtual
        tvValorUmidadeSolo.text = "${"%.1f".format(umidSolo)}%"
        val cor = corPorFaixaIdeal(umidSolo, faixaIdealUmidadeSolo)
        animarProgresso(progressUmidadeSolo, umidSolo.toInt(), cor)

        tvStatusUmidadeSolo.text = when {
            umidSolo <= 25f -> "Muito Seco"
            umidSolo <= 50f -> "Seco"
            umidSolo <= 75f -> "Ideal"
            else            -> "Encharcado"
        }
    }

    private fun atualizarTemperaturaSolo() {
        val tempSolo = temperaturaSoloAtual
        tvValorTemperaturaSolo.text = tempSolo.toDisplayTemp()
        val cor = corPorFaixaIdeal(tempSolo, faixaIdealTemperaturaSolo)
        animarProgresso(progressTemperaturaSolo, ((tempSolo / 90f) * 100).toInt(), cor)

        tvStatusTemperaturaSolo.text = when {
            tempSolo < 10f -> "Frio"
            tempSolo < 30f -> "Normal"
            tempSolo < 45f -> "Quente"
            else           -> "Crítico"
        }
    }

    private fun atualizarIrrigacao() {
        val irrigacao = irrigacaoAtual
        tvValorIrrigacao.text = "${"%.1f".format(irrigacao)}%"
        val cor = corPorFaixaIdeal(irrigacao, faixaIdealIrrigacao)
        animarProgresso(progressIrrigacao, irrigacao.toInt(), cor)

        tvStatusIrrigacao.text = when {
            irrigacao <= 20f -> "Baixo"
            irrigacao <= 60f -> "Normal"
            irrigacao <= 85f -> "Alto"
            else             -> "Excesso"
        }
    }

    private fun atualizarCartaoAmbiente() {
        val tempOk      = temperaturaAtual     in faixaIdealTemperatura
        val umidOk      = umidadeAtual         in faixaIdealUmidade
        val soloOk      = umidadeSoloAtual     in faixaIdealUmidadeSolo
        val tempSoloOk  = temperaturaSoloAtual in faixaIdealTemperaturaSolo
        val irrigacaoOk = irrigacaoAtual       in faixaIdealIrrigacao

        val alertas = listOf(tempOk, umidOk, soloOk, tempSoloOk, irrigacaoOk).count { !it }

        tvStatusAmbiente.text = when {
            alertas == 0 -> "Ótimo"
            alertas <= 2 -> "Regular"
            else         -> "Crítico"
        }

        exibirAlertaSeForaDaFaixa(ivAlertaTemperatura, tempOk)
        exibirAlertaSeForaDaFaixa(ivAlertaUmidade, umidOk)
        exibirAlertaSeForaDaFaixa(ivAlertaUmidadeSolo, soloOk)
        exibirAlertaSeForaDaFaixa(ivAlertaTemperaturaSolo, tempSoloOk)
        exibirAlertaSeForaDaFaixa(ivAlertaIrrigacao, irrigacaoOk)
    }

    private fun exibirAlertaSeForaDaFaixa(icone: ImageView, dentroDaFaixaIdeal: Boolean) {
        icone.visibility = if (dentroDaFaixaIdeal) View.GONE else View.VISIBLE
    }

    private fun atualizarCartaoCiclo() {
        val hora  = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val ehDia = hora in 6..17
        tvStatusCiclo.text = if (ehDia) "Dia" else "Noite"
    }

    private fun animarProgresso(barra: ProgressBar, alvoPercentual: Int, cor: Int) {
        val alvoLimitado = alvoPercentual.coerceIn(0, 100)
        colorirBarraProgresso(barra, cor)

        ObjectAnimator.ofInt(barra, "progress", barra.progress, alvoLimitado).apply {
            duration = 600
            start()
        }
    }

    /**
     * 4 níveis de cor de acordo com a faixa ideal daquela métrica específica:
     * abaixo do ideal = azul, dentro do ideal = verde,
     * um pouco acima = amarelo (atenção), muito acima = vermelho (crítico).
     */
    private fun corPorFaixaIdeal(valor: Float, faixaIdeal: ClosedFloatingPointRange<Float>): Int {
        val tamanhoDaFaixa = faixaIdeal.endInclusive - faixaIdeal.start
        val zonaDeAtencao  = faixaIdeal.endInclusive + (tamanhoDaFaixa / 2f)

        return when {
            valor < faixaIdeal.start   -> Color.parseColor("#3B7DC4") // abaixo do necessário
            valor <= faixaIdeal.endInclusive -> Color.parseColor("#4E7C59") // ideal
            valor <= zonaDeAtencao     -> Color.parseColor("#E0A83C") // atenção
            else                       -> Color.parseColor("#C0392B") // crítico
        }
    }

}