package com.example.procesamiento3d.api

import com.example.procesamiento3d.retrofit.TextureApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "http://192.168.1.13:8000/"

    val instance: TextureApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TextureApiService::class.java)
    }
}
