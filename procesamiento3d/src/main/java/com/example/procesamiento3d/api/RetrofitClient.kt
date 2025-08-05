package com.example.procesamiento3d.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "http://192.168.1.13:8000/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)  // tiempo para conectar
        .readTimeout(30, TimeUnit.SECONDS)     // tiempo para leer respuesta
        .readTimeout(60, TimeUnit.SECONDS)    // tiempo para esperar respuesta
        .build()

    val api: TextureApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TextureApiService::class.java)
    }
}
