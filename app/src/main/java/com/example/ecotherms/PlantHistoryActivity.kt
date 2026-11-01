package com.example.ecotherms

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
class PlantHistoryActivity : BaseActivity() {

    private lateinit var rvPlants: RecyclerView
    private lateinit var emptyState: View
    private lateinit var tvPlantCount: TextView

    private val historyList = mutableListOf<PlantHistoryEntry>()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_plant_history
        )

        rvPlants =
            findViewById(R.id.rvPlants)

        emptyState =
            findViewById(R.id.emptyState)

        tvPlantCount =
            findViewById(R.id.tvPlantCount)

        rvPlants.layoutManager =
            LinearLayoutManager(this)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        val irParaScanner = {
            startActivity(
                Intent(this, PlantScannerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        findViewById<ImageButton>(R.id.btn_add_from_counter).setOnClickListener { irParaScanner() }
        findViewById<Button>(R.id.btnGoToScanner).setOnClickListener { irParaScanner() }

        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        // Recarrega ao voltar do scanner, pra já mostrar uma planta recém-salva
        loadHistory()
    }

    private fun loadHistory() {

        val userKey = Firebase.auth.currentUser?.uid

        if (userKey == null) {

            showEmptyState()

            return
        }

        FirebaseDatabase
            .getInstance()
            .reference
            .child("users")
            .child(userKey)
            .child("history")
            .addListenerForSingleValueEvent(
                object : ValueEventListener {

                    override fun onDataChange(
                        snapshot: DataSnapshot
                    ) {

                        historyList.clear()

                        for (child in snapshot.children) {

                            val key = child.key ?: continue

                            val plant =
                                child.getValue(
                                    PlantHistory::class.java
                                )

                            if (plant != null) {
                                historyList.add(PlantHistoryEntry(key, plant))
                            }
                        }

                        updateUI()
                    }

                    override fun onCancelled(
                        error: DatabaseError
                    ) {

                        Toast.makeText(
                            this@PlantHistoryActivity,
                            "Erro ao carregar histórico",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
    }

    private fun updateUI() {

        tvPlantCount.text =
            historyList.size.toString()

        if (historyList.isEmpty()) {

            showEmptyState()

        } else {

            emptyState.visibility = View.GONE

            rvPlants.visibility = View.VISIBLE

            rvPlants.adapter =
                PlantHistoryAdapter(historyList) { entry ->
                    abrirDetalhes(entry)
                }
        }
    }
    private fun showEmptyState() {

        emptyState.visibility =
            View.VISIBLE

        rvPlants.visibility =
            View.GONE

        tvPlantCount.text = "0"
    }

    private fun abrirDetalhes(entry: PlantHistoryEntry) {
        val intent = Intent(this, PlantDetailActivity::class.java).apply {
            putExtra("plantKey", entry.key)
            putExtra("scientificName", entry.data.scientificName)
            putExtra("commonName", entry.data.commonName)
            putExtra("score", entry.data.score)
            putExtra("photoPath", entry.data.photoPath)
            putExtra("date", entry.data.date)
        }
        startActivity(intent)
    }
}
