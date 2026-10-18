package com.drivecinema.app.core.player

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

interface TokenProvider {
    fun getAccessToken(): String
    fun forceRefresh(): String
}

class TokenRefreshInterceptor(
    private val tokenProvider: TokenProvider,
    private val isTrustedDomain: (String) -> Boolean = ::isTrustedGoogleHost
) : Interceptor {

    companion object {
        const val MAX_REDIRECTS = 5

        private val GOOGLE_DOMAINS = listOf("googleapis.com", "googleusercontent.com", "google.com")
        private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1")

        /** A Google domain itself or a subdomain of it (`www.googleapis.com`), never a look-alike (`evilgoogle.com`). */
        fun isTrustedGoogleHost(host: String): Boolean {
            val h = host.lowercase()
            return h in LOOPBACK_HOSTS || GOOGLE_DOMAINS.any { h == it || h.endsWith(".$it") }
        }
    }

    /** The token only goes to trusted hosts, and only over HTTPS (plain HTTP is allowed for loopback test servers). */
    private fun mayReceiveToken(url: HttpUrl): Boolean =
        (url.isHttps || url.host in LOOPBACK_HOSTS) && isTrustedDomain(url.host)

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        var token = tokenProvider.getAccessToken()

        if (token.isEmpty()) {
            synchronized(this) {
                token = tokenProvider.getAccessToken()
                if (token.isEmpty()) {
                    token = tokenProvider.forceRefresh()
                }
            }
        }

        val authenticatedRequest = buildAuthenticatedRequest(originalRequest, token)
        val response = executeWithRedirects(chain, authenticatedRequest, token)

        if (response.code == 401) {
            response.close()
            synchronized(this) {
                val newToken = tokenProvider.forceRefresh()
                if (newToken.isNotEmpty()) {
                    val retryRequest = buildAuthenticatedRequest(originalRequest, newToken)
                    return executeWithRedirects(chain, retryRequest, newToken)
                }
            }
        }

        return response
    }

    private fun executeWithRedirects(
        chain: Interceptor.Chain,
        initialRequest: Request,
        token: String
    ): Response {
        var currentRequest = initialRequest
        var response = chain.proceed(currentRequest)
        var redirectCount = 0

        while (response.isRedirect && redirectCount < MAX_REDIRECTS) {
            val location = response.header("Location") ?: break
            response.close()
            redirectCount++

            val nextUrl = currentRequest.url.resolve(location) ?: break
            // newBuilder() copies the previous request's headers, so drop the token first and add it back only
            // for a trusted host.
            val nextRequestBuilder = currentRequest.newBuilder().url(nextUrl).removeHeader("Authorization")

            if (token.isNotEmpty() && mayReceiveToken(nextUrl)) {
                nextRequestBuilder.header("Authorization", "Bearer $token")
            }

            currentRequest = nextRequestBuilder.build()
            response = chain.proceed(currentRequest)
        }

        return response
    }

    private fun buildAuthenticatedRequest(request: Request, token: String): Request {
        return if (token.isNotEmpty() && mayReceiveToken(request.url)) {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }
    }
}
