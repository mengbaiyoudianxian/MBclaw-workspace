package com.mbclaw.nonroot.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 单例 — 动态切换服务器地址
 */
object NetworkModule {

    private var baseUrl: String = "http://47.83.2.188:8000"
    private var retrofit: Retrofit? = null
    private var apiService: MBclawApiService? = null

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    fun getService(): MBclawApiService {
        val current = apiService
        if (current != null) return current
        return buildService(baseUrl)
    }

    fun updateServerUrl(newUrl: String) {
        val normalized = newUrl.trimEnd('/')
        if (normalized == baseUrl && apiService != null) return
        baseUrl = normalized
        buildService(normalized)
    }

    fun getCurrentUrl(): String = baseUrl

    private fun buildService(url: String): MBclawApiService {
        retrofit = Retrofit.Builder()
            .baseUrl("$url/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit!!.create(MBclawApiService::class.java)
        return apiService!!
    }
}
