package com.example.data.api

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import com.example.data.db.JsonHelper

@JsonClass(generateAdapter = true)
data class HuggingFaceSearchResponse(
    val rows: List<HuggingFaceRowContainer>
)

@JsonClass(generateAdapter = true)
data class HuggingFaceRowContainer(
    val row_idx: Int,
    val row: HuggingFaceRow
)

@JsonClass(generateAdapter = true)
data class HuggingFaceRow(
    val question: String,
    val correct_answer: String,
    val distractor1: String,
    val distractor2: String,
    val distractor3: String,
    val support: String?
)

interface HuggingFaceService {
    @GET("search")
    suspend fun searchDataset(
        @Query("dataset") dataset: String = "sciq",
        @Query("config") config: String = "default",
        @Query("split") split: String = "train",
        @Query("query") query: String
    ): HuggingFaceSearchResponse
}

object HuggingFaceRetrofitClient {
    private const val BASE_URL = "https://datasets-server.huggingface.co/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val service: HuggingFaceService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(JsonHelper.moshi))
            .build()
            .create(HuggingFaceService::class.java)
    }
}
