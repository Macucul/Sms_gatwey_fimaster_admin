package com.example.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface FastApiService {
    @POST
    suspend fun registerUser(
        @Url url: String,
        @Header("Authorization") authorization: String?,
        @Body body: UserSyncPayload
    ): Response<Unit>

    @POST
    suspend fun uploadRawJson(
        @Url url: String,
        @Header("Authorization") authorization: String?,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Unit>

    @retrofit2.http.GET
    suspend fun getRawFile(
        @Url url: String,
        @Header("Authorization") authorization: String?
    ): Response<okhttp3.ResponseBody>
}

@JsonClass(generateAdapter = true)
data class UserSyncPayload(
    val id_usuario: String,
    val telefone: String,
    val nome: String,
    val conta: String,
    val id_transacao: String,
    val saldo: Double,
    val senha: String, // Stores the encrypted string or plaintext depending on settings
    val status: String,
    val data_registro: String
)
