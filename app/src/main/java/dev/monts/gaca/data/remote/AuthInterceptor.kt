package dev.monts.gaca.data.remote

import dev.monts.gaca.data.local.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/** Attaches `Authorization: Bearer <jwt>` per api-contract.md §1, when a token exists. */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.getTokenSync()
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
