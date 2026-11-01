package com.example.ecotherms

import androidx.core.content.ContextCompat
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.Firebase
import com.google.firebase.database.*
import com.google.firebase.auth.auth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivityDados : BaseActivity() {

    // ─── Views ───────────────────────────────────────────────────────────────
    private lateinit var graficoGeral         : LineChart
    private lateinit var txtNomeEstufa        : TextView
    private lateinit var txtUltimaAtualizacao : TextView
    private lateinit var tvTituloGrafico      : TextView
    private lateinit var tvMin                : TextView
    private lateinit var tvAtual              : TextView
    private lateinit var tvMax                : TextView

    private lateinit var chipTemperatura      : TextView
    private lateinit var chipUmidade          : TextView
    private lateinit var chipUmidadeSolo      : TextView
    private lateinit var chipTemperaturaSolo  : TextView
    private lateinit var chipIrrigacao        : TextView

    private lateinit var botao1h              : Button
    private lateinit var botao6h              : Button
    private lateinit var botao24h             : Button
    private lateinit var botao7d              : Button

    private lateinit var navHome              : LinearLayout
    private lateinit var navChart             : LinearLayout
    private lateinit var navPlants            : LinearLayout
    private lateinit var navProfile           : LinearLayout

    // ─── Cores ───────────────────────────────────────────────────────────────
    private val corAtivo   = Color.parseColor("#1E3A29")
    private val corInativo = Color.parseColor("#7A8C82")
    private val corGrafico = Color.parseColor("#4E7C59")

    // ─── Estado ──────────────────────────────────────────────────────────────
    private var periodoSelecionado  = "1H"
    private var metricaSelecionada  = "temperatura"

    // ─── Unidade ─────────────────────────────────────────────────────────────
    private var usarFahrenheit: Boolean = false

    // ─── Broadcast receiver ──────────────────────────────────────────────────
    private val receptorUnidadeTemp = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PreferencesActivity.ACTION_TEMP_UNIT_CHANGED) {
                usarFahrenheit = intent.getBooleanExtra(
                    PreferencesActivity.EXTRA_USE_FAHRENHEIT, false
                )
                renderizarGrafico()
            }
        }
    }

    // ─── Dados ───────────────────────────────────────────────────────────────
    private lateinit var referenciaHistorico : DatabaseReference
    private lateinit var ouvinteBancoDados   : ValueEventListener

    private val listaTemperatura     = mutableListOf<Pair<String, Float>>()
    private val listaUmidade         = mutableListOf<Pair<String, Float>>()
    private val listaUmidadeSolo     = mutableListOf<Pair<String, Float>>()
    private val listaTemperaturaSolo = mutableListOf<Pair<String, Float>>()
    private val listaIrrigacao       = mutableListOf<Pair<String, Float>>()

    // ─── Lifecycle ────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_dados)
        supportActionBar?.hide()

        // Lê preferência salva
        usarFahrenheit = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getBoolean("fahrenheit", false)

        iniciarViews()
        configurarChips()
        configurarBotoesPeriodo()
        configurarNavBar()
        configurarDrawer()
        configurarFirebase()
    }

    override fun onResume() {
        super.onResume()
        val filtro = IntentFilter(PreferencesActivity.ACTION_TEMP_UNIT_CHANGED)
        ContextCompat.registerReceiver(
            this,
            receptorUnidadeTemp,
            filtro,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Relê caso tenha mudado sem broadcast
        usarFahrenheit = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getBoolean("fahrenheit", false)
        renderizarGrafico()
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(receptorUnidadeTemp) }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::ouvinteBancoDados.isInitialized && ::referenciaHistorico.isInitialized) {
            referenciaHistorico.removeEventListener(ouvinteBancoDados)
        }
    }

    // ─── Conversão ────────────────────────────────────────────────────────────
    /** Converte °C → °F se necessário. Os dados do Firebase ficam sempre em °C. */
    private fun Float.toDisplayTemp(): Float =
        if (usarFahrenheit) this * 9f / 5f + 32f else this

    private val unidadeTemp: String
        get() = if (usarFahrenheit) "°F" else "°C"

    // ─── Views ────────────────────────────────────────────────────────────────
    private fun iniciarViews() {
        graficoGeral         = findViewById(R.id.chartGeral)
        txtNomeEstufa        = findViewById(R.id.tvGreenhouseName)
        txtUltimaAtualizacao = findViewById(R.id.tvLastUpdate)
        tvTituloGrafico      = findViewById(R.id.tvTituloGrafico)
        tvMin                = findViewById(R.id.tvMin)
        tvAtual              = findViewById(R.id.tvAtual)
        tvMax                = findViewById(R.id.tvMax)

        chipTemperatura     = findViewById(R.id.chipTemperatura)
        chipUmidade         = findViewById(R.id.chipUmidade)
        chipUmidadeSolo     = findViewById(R.id.chipUmidadeSolo)
        chipTemperaturaSolo = findViewById(R.id.chipTemperaturaSolo)
        chipIrrigacao       = findViewById(R.id.chipIrrigacao)

        botao1h  = findViewById(R.id.btn1h)
        botao6h  = findViewById(R.id.btn6h)
        botao24h = findViewById(R.id.btn24h)
        botao7d  = findViewById(R.id.btn7d)

        navHome    = findViewById(R.id.navHome)
        navChart   = findViewById(R.id.navChart)
        navPlants  = findViewById(R.id.navPlants)
        navProfile = findViewById(R.id.navProfile)

        txtNomeEstufa.text = intent.getStringExtra("greenhouse_name") ?: "Estufa"
    }

    // ─── Chips ───────────────────────────────────────────────────────────────
    private fun configurarChips() {
        val chips = mapOf(
            chipTemperatura     to "temperatura",
            chipUmidade         to "umidade",
            chipUmidadeSolo     to "umidadeSolo",
            chipTemperaturaSolo to "temperaturaSolo",
            chipIrrigacao       to "irrigacao"
        )
        chips.forEach { (chip, metrica) ->
            chip.setOnClickListener {
                metricaSelecionada = metrica
                atualizarEstadoChips(chip)
                renderizarGrafico()
            }
        }
        atualizarEstadoChips(chipTemperatura)
    }

    private fun atualizarEstadoChips(chipAtivo: TextView) {
        listOf(chipTemperatura, chipUmidade, chipUmidadeSolo, chipTemperaturaSolo, chipIrrigacao)
            .forEach { chip ->
                chip.setBackgroundResource(
                    if (chip == chipAtivo) R.drawable.background_button_enabled
                    else R.drawable.background_button_disabled
                )
            }
    }

    // ─── Período ──────────────────────────────────────────────────────────────
    private fun configurarBotoesPeriodo() {
        botao1h.setOnClickListener  { selecionarPeriodo("1H")  }
        botao6h.setOnClickListener  { selecionarPeriodo("6H")  }
        botao24h.setOnClickListener { selecionarPeriodo("24H") }
        botao7d.setOnClickListener  { selecionarPeriodo("7D")  }

        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            if (::referenciaHistorico.isInitialized && ::ouvinteBancoDados.isInitialized) {
                referenciaHistorico.removeEventListener(ouvinteBancoDados)
                referenciaHistorico.addValueEventListener(ouvinteBancoDados)
            }
        }
    }

    private fun selecionarPeriodo(periodo: String) {
        periodoSelecionado = periodo
        atualizarEstadoBotoesPeriodo()
        renderizarGrafico()
    }

    private fun atualizarEstadoBotoesPeriodo() {
        listOf(botao1h, botao6h, botao24h, botao7d).forEach {
            it.backgroundTintList = ColorStateList.valueOf(corInativo)
        }
        when (periodoSelecionado) {
            "1H"  -> botao1h.backgroundTintList  = ColorStateList.valueOf(corAtivo)
            "6H"  -> botao6h.backgroundTintList  = ColorStateList.valueOf(corAtivo)
            "24H" -> botao24h.backgroundTintList = ColorStateList.valueOf(corAtivo)
            "7D"  -> botao7d.backgroundTintList  = ColorStateList.valueOf(corAtivo)
        }
    }

    // ─── NavBar ───────────────────────────────────────────────────────────────
    private fun configurarNavBar() {
        navHome.setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        navChart.setOnClickListener { }
        navPlants.setOnClickListener {
            startActivity(
                Intent(this, PlantScannerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        navProfile.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    // ─── Drawer (menu lateral) ──────────────────────────────────────────────────
    private fun configurarDrawer() {
        val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)

        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }

        findViewById<ImageButton>(R.id.btnNotifications).setOnClickListener {
            Toast.makeText(this, "Nenhuma notificação por enquanto", Toast.LENGTH_SHORT).show()
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

    // ─── Firebase ─────────────────────────────────────────────────────────────
    /**
     * IMPORTANTE: o pipeline (ESP32 simulado → HiveMQ → Firebase) só escreve os
     * valores ATUAIS da estufa em "estufas/estufa1" — os mesmos que a tela Inicial
     * lê. Não existe um nó "historico" populado por esse pipeline; por isso o
     * gráfico nunca aparecia.
     *
     * A solução: ouvir esse MESMO caminho aqui, e a cada leitura nova que chegar,
     * ACUMULAR um ponto no histórico local desta tela (com a hora do momento em
     * que chegou), em vez de esperar por uma lista pronta que nunca vem. O gráfico
     * cresce sozinho enquanto o app fica aberto recebendo dados novos.
     */
    private val maximoPontosArmazenados = 500

    private fun configurarFirebase() {
        referenciaHistorico = Firebase.database.getReference("estufas/estufa1")

        val formatoHora = SimpleDateFormat("HH:mm:ss", Locale("pt", "BR"))

        ouvinteBancoDados = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val agora = formatoHora.format(Date())

                adicionarPonto(listaTemperatura,     agora, lerFloat(snapshot, "temperatura"))
                adicionarPonto(listaUmidade,         agora, lerFloat(snapshot, "umidade"))
                adicionarPonto(listaUmidadeSolo,     agora, lerFloat(snapshot, "umidadeSolo"))
                adicionarPonto(listaTemperaturaSolo, agora, lerFloat(snapshot, "temperaturaSolo"))
                adicionarPonto(listaIrrigacao,        agora, lerFloat(snapshot, "irrigacao"))

                txtUltimaAtualizacao.text = "Agora"
                renderizarGrafico()
            }
            override fun onCancelled(erro: DatabaseError) {
                Log.e("Firebase", "Erro: ${erro.message}")
                Toast.makeText(this@MainActivityDados, "Erro ao carregar histórico: ${erro.message}", Toast.LENGTH_LONG).show()
            }
        }

        referenciaHistorico.addValueEventListener(ouvinteBancoDados)
    }

    /** Acrescenta um ponto novo no fim da lista, limitando o tamanho pra não crescer pra sempre. */
    private fun adicionarPonto(lista: MutableList<Pair<String, Float>>, hora: String, valor: Float) {
        lista.add(hora to valor)
        if (lista.size > maximoPontosArmazenados) {
            lista.removeAt(0)
        }
    }

    private fun lerFloat(snapshot: DataSnapshot, campo: String): Float {
        val valor = snapshot.child(campo).value ?: return 0f
        return when (valor) {
            is Number -> valor.toFloat()
            is String -> valor.replace(',', '.').toFloatOrNull() ?: 0f
            is Boolean -> if (valor) 1f else 0f
            else -> 0f
        }
    }

    // ─── Gráfico ──────────────────────────────────────────────────────────────
    private fun renderizarGrafico() {
        val dadosCompletos = when (metricaSelecionada) {
            "temperatura"     -> listaTemperatura
            "umidade"         -> listaUmidade
            "umidadeSolo"     -> listaUmidadeSolo
            "temperaturaSolo" -> listaTemperaturaSolo
            "irrigacao"       -> listaIrrigacao
            else              -> listaTemperatura
        }

        if (dadosCompletos.isEmpty()) {
            tvTituloGrafico.text = "Aguardando dados do histórico"
            tvMin.text = "--"
            tvAtual.text = "--"
            tvMax.text = "--"
            graficoGeral.clear()
            graficoGeral.invalidate()
            return
        }

        val limite = when (periodoSelecionado) {
            "1H"  -> 6
            "6H"  -> 36
            "24H" -> 144
            else  -> dadosCompletos.size
        }

        val dadosFiltrados = dadosCompletos.takeLast(limite)
        val rotulos        = dadosFiltrados.map { it.first }

        // Determina se a métrica atual usa temperatura
        val ehMetricaTemp = metricaSelecionada == "temperatura" || metricaSelecionada == "temperaturaSolo"

        // Converte os valores para exibição
        val valores = dadosFiltrados.map { (_, v) ->
            if (ehMetricaTemp) v.toDisplayTemp() else v
        }

        val (titulo, unidade) = when (metricaSelecionada) {
            "temperatura"     -> "Temperatura do Ar"   to unidadeTemp
            "umidade"         -> "Umidade do Ar"       to "%"
            "umidadeSolo"     -> "Umidade do Solo"     to "%"
            "temperaturaSolo" -> "Temperatura do Solo" to unidadeTemp
            "irrigacao"       -> "Irrigação"            to "%"
            else              -> "Dado"                 to ""
        }

        tvTituloGrafico.text = "$titulo ($unidade)"
        tvMin.text   = "${valores.minOrNull()?.toInt() ?: "--"}$unidade"
        tvAtual.text = "${valores.lastOrNull()?.toInt() ?: "--"}$unidade"
        tvMax.text   = "${valores.maxOrNull()?.toInt() ?: "--"}$unidade"

        val entradas = valores.mapIndexed { i, v -> Entry(i.toFloat(), v) }

        val serie = LineDataSet(entradas, titulo).apply {
            color = corGrafico
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth    = 3f
            setDrawFilled(true)

            // Gradiente do verde da marca até transparente, em vez de uma cor chapada
            fillDrawable = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#664E7C59"), Color.parseColor("#004E7C59"))
            )

            mode = if (entradas.size > 1) LineDataSet.Mode.CUBIC_BEZIER else LineDataSet.Mode.LINEAR
            cubicIntensity = 0.18f
            setDrawHorizontalHighlightIndicator(false)
            highLightColor = corGrafico
        }

        graficoGeral.apply {
            data = LineData(serie)

            xAxis.apply {
                valueFormatter  = IndexAxisValueFormatter(rotulos)
                position        = XAxis.XAxisPosition.BOTTOM
                granularity     = 1f
                setDrawGridLines(false)
                textColor       = Color.parseColor("#7A8C82")
                textSize        = 9f
                axisLineColor   = Color.parseColor("#D5DBD1")
                setLabelCount(4, false)
            }
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor     = Color.parseColor("#1A1E3A29")
                textColor     = Color.parseColor("#7A8C82")
                axisLineColor = Color.parseColor("#D5DBD1")
                setDrawZeroLine(false)
            }
            axisRight.isEnabled   = false
            description.isEnabled = false
            legend.isEnabled      = false
            setExtraOffsets(4f, 8f, 4f, 4f)
            setTouchEnabled(true)
            setPinchZoom(true)
            animateX(500)
            invalidate()
        }
    }
}