package com.drivecinema.app.core.player

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import okhttp3.OkHttpClient

/**
 * Single data source for the player. http(s) URIs go through the unchanged Drive stack
 * ([DriveDataSourceFactory]: token refresh + Authorization stripping on untrusted redirects);
 * local `content://` URIs (from the Storage Access Framework) are read via Media3's ContentDataSource.
 */
object PlaybackDataSourceFactory {
    fun create(
        context: Context,
        tokenProvider: TokenProvider,
        baseClient: OkHttpClient = OkHttpClient()
    ): DataSource.Factory =
        DefaultDataSource.Factory(context, DriveDataSourceFactory.create(tokenProvider, baseClient))
}
