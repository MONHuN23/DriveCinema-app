package com.drivecinema.desktop.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** PKCE pair (RFC 7636, S256): the verifier stays in the app, the challenge goes to Google. */
data class Pkce(val verifier: String, val challenge: String) {
    companion object {
        private val random = SecureRandom()
        private val encoder = Base64.getUrlEncoder().withoutPadding()

        fun generate(): Pkce = fromVerifier(randomToken(32))

        fun fromVerifier(verifier: String): Pkce {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return Pkce(verifier, encoder.encodeToString(digest))
        }

        /** URL-safe random string (32 bytes -> 43 characters). Also used for the OAuth `state`. */
        fun randomToken(bytes: Int = 32): String {
            val buffer = ByteArray(bytes)
            random.nextBytes(buffer)
            return encoder.encodeToString(buffer)
        }
    }
}
