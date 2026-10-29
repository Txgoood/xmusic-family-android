package com.example.data.source

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object AudiusClient {
    private const val BASE_URL = "https://discovery-us-01.audius.openplayer.org/"
    
    val api: AudiusApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(AudiusApi::class.java)
    }
}
