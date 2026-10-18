package com.drivecinema.app.core.player

import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object DriveDataSourceFactory {
    fun create(
        tokenProvider: TokenProvider,
        baseClient: OkHttpClient = OkHttpClient()
    ): HttpDataSource.Factory {
        val client = baseClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(TokenRefreshInterceptor(tokenProvider))
            .build()

        return OkHttpDataSource.Factory(client)
            .setUserAgent("DriveCinema/1.0 (Android)")
    }
}
