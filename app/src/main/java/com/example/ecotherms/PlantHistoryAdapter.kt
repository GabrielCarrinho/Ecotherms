package com.example.ecotherms

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class PlantHistoryAdapter(
    private val entries: List<PlantHistoryEntry>,
    private val onItemClick: (PlantHistoryEntry) -> Unit
) : RecyclerView.Adapter<PlantHistoryAdapter.PlantViewHolder>() {

    class PlantViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        val imgPlant: ImageView =
            itemView.findViewById(R.id.imgPlant)

        val tvPlantNumber: TextView =
            itemView.findViewById(R.id.tvPlantNumber)

        val tvScientificName: TextView =
            itemView.findViewById(R.id.tvScientificName)

        val tvCommonName: TextView =
            itemView.findViewById(R.id.tvCommonName)

        val tvScore: TextView =
            itemView.findViewById(R.id.tvScore)

        val tvDate: TextView =
            itemView.findViewById(R.id.tvDate)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PlantViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(
                R.layout.item_plant_history,
                parent,
                false
            )

        return PlantViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: PlantViewHolder,
        position: Int
    ) {

        val entry = entries[position]
        val plant = entry.data

        holder.tvPlantNumber.text = String.format("%02d", position + 1)

        val temNomePopular = plant.commonName.isNotBlank() && plant.commonName != "-"

        if (temNomePopular) {
            holder.tvCommonName.text = plant.commonName
            holder.tvScientificName.text = plant.scientificName
            holder.tvScientificName.visibility = View.VISIBLE
        } else {
            // Sem nome popular retornado pela API: nome científico vira o título principal
            holder.tvCommonName.text = plant.scientificName
            holder.tvScientificName.visibility = View.GONE
        }

        holder.tvDate.text = plant.date

        // Score vem como "0.87" (0 a 1) — exibe como porcentagem
        val scorePercent = plant.score.replace(',', '.').toDoubleOrNull()?.let { (it * 100).toInt() }
        holder.tvScore.text = if (scorePercent != null) "$scorePercent%" else plant.score

        // Carregar foto salva, com placeholder caso o arquivo não exista mais
        val file = if (plant.photoPath.isNotBlank()) File(plant.photoPath) else null
        if (file != null && file.exists()) {
            holder.imgPlant.setImageURI(Uri.fromFile(file))
        } else {
            holder.imgPlant.setImageResource(R.drawable.ic_plant)
        }

        holder.itemView.setOnClickListener { onItemClick(entry) }
    }

    override fun getItemCount(): Int {
        return entries.size
    }
}
