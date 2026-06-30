package app.pwhs.inject.frida.root

import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File

class FridaRootManager {

    companion object {
        private const val TAG = "FridaRootManager"
        private const val FRIDA_SERVER_PATH = "/data/local/tmp/frida-server"
    }

    init {
        // Configure libsu
        Shell.enableVerboseLogging = true
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }

    fun isRootGranted(): Boolean {
        return Shell.getShell().isRoot
    }

    fun killOldServer() {
        Log.d(TAG, "Killing old frida-server instances...")
        Shell.cmd("killall frida-server").exec()
    }

    fun copyAndChmod(sourceFile: File): Boolean {
        Log.d(TAG, "Copying frida-server to $FRIDA_SERVER_PATH")
        if (!sourceFile.exists()) {
            Log.e(TAG, "Source file does not exist: ${sourceFile.absolutePath}")
            return false
        }

        val result = Shell.cmd(
            "cp ${sourceFile.absolutePath} $FRIDA_SERVER_PATH",
            "chmod 755 $FRIDA_SERVER_PATH"
        ).exec()

        if (!result.isSuccess) {
            Log.e(TAG, "Failed to copy and chmod: ${result.out}")
            return false
        }
        return true
    }

    fun startServer(): Boolean {
        Log.d(TAG, "Starting frida-server in background...")
        // We use su -c to ensure it runs in the background and detached from the app process
        val result = Shell.cmd("su -c '$FRIDA_SERVER_PATH &'").exec()
        if (result.isSuccess) {
            Log.d(TAG, "Frida server started successfully.")
            return true
        } else {
            Log.e(TAG, "Failed to start server: ${result.out}")
            return false
        }
    }
}
