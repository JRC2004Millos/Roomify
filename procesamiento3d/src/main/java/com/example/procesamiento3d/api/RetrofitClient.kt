package com.example.procesamiento3d.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

<<<<<<< HEAD
    private const val BASE_URL = "http://192.168.5.216:8000/"
=======
    private const val BASE_URL = "http://192.168.0.7:8000/"
>>>>>>> 780dc069e45b3d088736d3897dfc963f4abb946d

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
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
