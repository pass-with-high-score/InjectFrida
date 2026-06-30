package app.pwhs.inject.frida.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.pwhs.inject.frida.data.api.GithubApiService
import app.pwhs.inject.frida.data.repository.GithubRepository
import app.pwhs.inject.frida.root.FridaRootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.FileOutputStream

class DownloadAndInjectWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val githubRepository: GithubRepository by inject()
    private val apiService: GithubApiService by inject()
    private val rootManager: FridaRootManager by inject()

    companion object {
        private const val TAG = "InjectWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Worker started. Checking root access...")
            if (!rootManager.isRootGranted()) {
                Log.e(TAG, "Root access not granted. Aborting.")
                return@withContext Result.failure()
            }

            val targetVersion = inputData.getString("TARGET_VERSION")

            Log.d(TAG, "Fetching release info...")
            val downloadUrl = githubRepository.getFridaServerAssetUrl(targetVersion)
            if (downloadUrl == null) {
                Log.e(TAG, "Failed to get download URL.")
                return@withContext Result.failure()
            }

            Log.d(TAG, "Downloading from: $downloadUrl")
            val downloadResponse = apiService.downloadFile(downloadUrl)
            
            if (!downloadResponse.isSuccessful || downloadResponse.body() == null) {
                Log.e(TAG, "Failed to download file.")
                return@withContext Result.failure()
            }

            val cacheDir = applicationContext.cacheDir
            val xzFile = File(cacheDir, "frida-server.xz")
            val binFile = File(cacheDir, "frida-server")

            // 1. Save downloaded stream to .xz file
            Log.d(TAG, "Saving to ${xzFile.absolutePath}")
            downloadResponse.body()!!.byteStream().use { inputStream ->
                FileOutputStream(xzFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // 2. Decompress .xz to binary
            Log.d(TAG, "Decompressing xz file...")
            XZInputStream(xzFile.inputStream()).use { xzIn ->
                FileOutputStream(binFile).use { out ->
                    xzIn.copyTo(out)
                }
            }

            // 3. Root Injection
            Log.d(TAG, "Injecting Frida server via root...")
            rootManager.killOldServer()
            val copied = rootManager.copyAndChmod(binFile)
            if (!copied) {
                return@withContext Result.failure()
            }

            val started = rootManager.startServer()
            if (started) {
                Log.d(TAG, "Successfully injected and started Frida server.")
                // Cleanup
                xzFile.delete()
                binFile.delete()
                return@withContext Result.success()
            } else {
                return@withContext Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed with exception", e)
            return@withContext Result.failure()
        }
    }
}
