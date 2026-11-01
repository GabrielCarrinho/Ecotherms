package com.example.ecotherms

data class PlantHistory(
    val scientificName: String = "",
    val commonName: String = "",
    val score: String = "",
    val photoPath: String = "",
    val date: String = ""
)

/** Junta o dado salvo com a chave gerada pelo Firebase (.push()), necessária para editar/excluir. */
data class PlantHistoryEntry(
    val key: String,
    val data: PlantHistory
)