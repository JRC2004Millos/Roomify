package com.example.procesamiento3d.api

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface TextureApiService {
    @Multipart
    @POST("predict/")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part
    ): Response<TextureResponse>
}
