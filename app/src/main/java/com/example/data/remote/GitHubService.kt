package com.example.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubService {
    @GET("repos/{owner}/{repo}/contents/{filePath}")
    suspend fun getFileMetadata(
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Header("User-Agent") userAgent: String = "SMS-Gateway-Pro",
        @Header("X-GitHub-Api-Version") apiVersion: String = "2022-11-28",
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("filePath") filePath: String,
        @Query("ref") ref: String? = null
    ): Response<GitHubFileMetadata>

    @GET("repos/{owner}/{repo}/contents/{dirPath}")
    suspend fun getDirectoryContents(
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Header("User-Agent") userAgent: String = "SMS-Gateway-Pro",
        @Header("X-GitHub-Api-Version") apiVersion: String = "2022-11-28",
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("dirPath") dirPath: String,
        @Query("ref") ref: String? = null
    ): Response<List<GitHubFileMetadata>>

    @PUT("repos/{owner}/{repo}/contents/{filePath}")
    suspend fun createOrUpdateFile(
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Header("User-Agent") userAgent: String = "SMS-Gateway-Pro",
        @Header("X-GitHub-Api-Version") apiVersion: String = "2022-11-28",
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("filePath") filePath: String,
        @Body body: GitHubPutBody
    ): Response<GitHubPutResponse>
}

@JsonClass(generateAdapter = true)
data class GitHubFileMetadata(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long,
    val type: String,
    val content: String? = null,
    val encoding: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubPutBody(
    val message: String,
    val content: String, // Base64 encoded string
    val branch: String,
    val sha: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubPutResponse(
    val content: GitHubContentInfo?
)

@JsonClass(generateAdapter = true)
data class GitHubContentInfo(
    val name: String,
    val path: String,
    val sha: String
)
