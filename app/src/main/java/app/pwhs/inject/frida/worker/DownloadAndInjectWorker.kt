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
    private val gadgetManager: app.pwhs.inject.frida.gadget.FridaGadgetManager by inject()

    companion object {
        private const val TAG = "InjectWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val targetVersion = inputData.getString("TARGET_VERSION")
            val port = inputData.getString("PORT") ?: "27042"
            val stealthMode = inputData.getBoolean("STEALTH_MODE", false)
            val injectionMode = inputData.getInt("INJECTION_MODE", 0)

            if (injectionMode == 1) { // NON_ROOT_GADGET
                val targetApk = inputData.getString("TARGET_APK_PATH")
                val customUri = inputData.getString("CUSTOM_FRIDA_URI")
                if (targetApk.isNullOrEmpty()) {
                    Log.e(TAG, "Target APK path is empty.")
                    return@withContext Result.failure()
                }
                
                // TODO: Here we would pass log updates to UI. Since Worker logs are limited, 
                // we assume GadgetManager handles it or we log to logcat.
                val success = gadgetManager.processApk(targetApk, targetVersion, customUri) { msg ->
                    Log.d(TAG, msg)
                }
                
                if (success) {
                    return@withContext Result.success()
                } else {
                    return@withContext Result.failure()
                }
            }

            Log.d(TAG, "Worker started. Checking root access for Root Injection...")
            if (!rootManager.isRootGranted()) {
                Log.e(TAG, "Root access not granted. Aborting.")
                return@withContext Result.failure()
            }

            Log.d(TAG, "Fetching release info for Root Injection...")
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
            val body = downloadResponse.body()!!
            val totalBytes = body.contentLength()
            body.byteStream().use { inputStream ->
                FileOutputStream(xzFile).use { outputStream ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastProgress = 0
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            val progress = ((totalRead * 100) / totalBytes).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                setProgress(androidx.work.Data.Builder().putInt("PROGRESS", progress).build())
                            }
                        }
                    }
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
            rootManager.stopFridaServer()
            
            val targetPath = rootManager.copyAndChmod(binFile, stealthMode)
            if (targetPath == null) {
                return@withContext Result.failure()
            }

            val started = rootManager.startServer(targetPath, port)
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
