package app.pwhs.inject.frida.data.repository

import android.os.Build
import android.util.Log
import app.pwhs.inject.frida.data.api.GithubApiService
import app.pwhs.inject.frida.data.model.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GithubRepository(private val apiService: GithubApiService) {

    companion object {
        private const val TAG = "GithubRepository"
    }
    
    suspend fun getAvailableVersions(): List<String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getReleases()
            if (response.isSuccessful && response.body() != null) {
                return@withContext response.body()!!.map { it.tagName }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting releases", e)
        }
        return@withContext emptyList()
    }

    suspend fun getFridaServerAssetUrl(targetVersion: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            val release: GithubRelease? = if (targetVersion == null) {
                val response = apiService.getLatestRelease()
                if (response.isSuccessful) response.body() else null
            } else {
                val response = apiService.getReleases()
                if (response.isSuccessful) response.body()?.find { it.tagName == targetVersion } else null
            }

            if (release != null) {
                val tagName = release.tagName
                
                // Get the primary ABI of the device (e.g., arm64-v8a, armeabi-v7a, x86_64, x86)
                val deviceAbi = Build.SUPPORTED_ABIS[0]
                
                // Map Android ABI to Frida asset naming convention
                val fridaArch = when {
                    deviceAbi.contains("arm64") -> "arm64"
                    deviceAbi.contains("armeabi") -> "arm"
                    deviceAbi.contains("x86_64") -> "x86_64"
                    deviceAbi.contains("x86") -> "x86"
                    else -> "arm64" // fallback
                }

                // E.g., frida-server-16.1.4-android-arm64.xz
                val targetAssetName = "frida-server-${tagName.removePrefix("v")}-android-$fridaArch.xz"
                Log.d(TAG, "Looking for asset: $targetAssetName")

                val targetAsset = release.assets.find { it.name == targetAssetName }
                
                if (targetAsset != null) {
                    Log.d(TAG, "Found download URL: ${targetAsset.downloadUrl}")
                    return@withContext targetAsset.downloadUrl
                } else {
                    Log.e(TAG, "Asset $targetAssetName not found in release $tagName")
                }
            } else {
                Log.e(TAG, "Failed to fetch release info.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting release", e)
        }
        return@withContext null
    }
}
