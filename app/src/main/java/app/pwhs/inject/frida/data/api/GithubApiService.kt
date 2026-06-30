package app.pwhs.inject.frida.data.api

import app.pwhs.inject.frida.data.model.GithubRelease
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

interface GithubApiService {
    @GET("repos/frida/frida/releases/latest")
    suspend fun getLatestRelease(): Response<GithubRelease>

    @GET("repos/frida/frida/releases")
    suspend fun getReleases(): Response<List<GithubRelease>>

    @Streaming
    @GET
    suspend fun downloadFile(@Url fileUrl: String): Response<ResponseBody>
}
