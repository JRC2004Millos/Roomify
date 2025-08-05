// retrofit/TextureApiService.kt
package com.example.procesamiento3d.retrofit

import com.example.procesamiento3d.api.TextureResponse
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.Call
import retrofit2.http.Headers

interface TextureApiService {
    @Multipart
    @POST("predict/")
    fun uploadImage(
        @Part image: MultipartBody.Part
    ): Call<TextureResponse>
}

