package com.example.ecotherms.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

data class PlantNetResponse(val results: List<PlantNetResult>)
data class PlantNetResult(val score: Double, val species: PlantNetSpecies)
data class PlantNetSpecies(
    val scientificNameWithoutAuthor: String,
    val commonNames: List<String>?
)

interface PlantNetApi {
    @Multipart
    @POST("v2/identify/all")
    fun identify(
        @Query("api-key") apiKey: String,
        @Query("lang") lang: String,
        @Part image: MultipartBody.Part,
        @Part("organs") organs: RequestBody
    ): Call<PlantNetResponse>
}