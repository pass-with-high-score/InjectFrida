package app.pwhs.inject.frida.root

import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File
import java.util.UUID

class FridaRootManager {

    companion object {
        private const val TAG = "FridaRootManager"
        private const val FRIDA_TMP_DIR = "/data/local/tmp"
        private const val BOOT_SCRIPT_PATH = "/data/adb/service.d/frida-server.sh"
    }

    init {
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

    fun isFridaRunning(): Boolean {
        // pidof usually returns empty string if not found, or PID(s) if found
        val result = Shell.cmd("pidof frida-server").exec()
        val out = result.out.joinToString("").trim()
        if (out.isNotEmpty() && out.all { it.isDigit() || it.isWhitespace() }) return true
        
        // Check for stealth names if any (heuristic: look for listening ports commonly used, but pidof is enough if we know the stealth name)
        // A simple way is to check if port is listening, but we don't always know the port here.
        return false
    }
    
    fun getRunningStealthServerPids(): List<String> {
        // Simple heuristic to find running stealth servers: check files in /data/local/tmp that are running
        val result = Shell.cmd("lsof -i | grep frida").exec() // This might not catch renamed stealth, but netstat might.
        return emptyList() // For now, we will just use a specific stealth name or killall -9
    }

    fun stopFridaServer() {
        Log.d(TAG, "Stopping frida-server...")
        // Kill standard name
        Shell.cmd("killall -9 frida-server").exec()
        // Kill potential stealth names by checking what is running from /data/local/tmp
        val pidsResult = Shell.cmd("ps -ef | grep /data/local/tmp/ | grep -v grep | awk '{print \$2}'").exec()
        if (pidsResult.isSuccess && pidsResult.out.isNotEmpty()) {
            val pids = pidsResult.out.joinToString(" ")
            Shell.cmd("kill -9 $pids").exec()
        }
    }

    fun cleanUpFrida() {
        Log.d(TAG, "Cleaning up $FRIDA_TMP_DIR")
        stopFridaServer()
        Shell.cmd("rm -rf $FRIDA_TMP_DIR/frida*").exec()
        Shell.cmd("rm -rf $FRIDA_TMP_DIR/system_daemon*").exec()
    }

    fun copyAndChmod(sourceFile: File, stealthMode: Boolean): String? {
        val targetName = if (stealthMode) {
            "system_daemon_${UUID.randomUUID().toString().substring(0, 8)}"
        } else {
            "frida-server"
        }
        val targetPath = "$FRIDA_TMP_DIR/$targetName"
        
        Log.d(TAG, "Copying frida-server to $targetPath")
        if (!sourceFile.exists()) {
            Log.e(TAG, "Source file does not exist: ${sourceFile.absolutePath}")
            return null
        }

        val result = Shell.cmd(
            "cp ${sourceFile.absolutePath} $targetPath",
            "chmod 755 $targetPath"
        ).exec()

        if (!result.isSuccess) {
            Log.e(TAG, "Failed to copy and chmod: ${result.out}")
            return null
        }
        return targetPath
    }

    fun startServer(executablePath: String, port: String): Boolean {
        stopFridaServer() // Ensure no old server is running
        
        val bindAddress = "0.0.0.0:$port"
        Log.d(TAG, "Starting frida-server at $executablePath on $bindAddress")
        
        val result = Shell.cmd("su -c '$executablePath -l $bindAddress &'").exec()
        if (result.isSuccess) {
            Log.d(TAG, "Frida server started successfully.")
            return true
        } else {
            Log.e(TAG, "Failed to start server: ${result.out}")
            return false
        }
    }

    fun toggleBootScript(enable: Boolean, port: String, stealthMode: Boolean) {
        if (enable) {
            val execName = if (stealthMode) "system_daemon_boot" else "frida-server"
            val scriptContent = """
                #!/system/bin/sh
                # Frida Auto-start Script
                wait_until_boot_complete() {
                  while [ "$(getprop sys.boot_completed)" != "1" ]; do
                    sleep 1
                  done
                }
                wait_until_boot_complete
                /data/local/tmp/$execName -l 0.0.0.0:$port &
            """.trimIndent()
            
            Shell.cmd(
                "echo '$scriptContent' > $BOOT_SCRIPT_PATH",
                "chmod 755 $BOOT_SCRIPT_PATH"
            ).exec()
            Log.d(TAG, "Boot script enabled at $BOOT_SCRIPT_PATH")
        } else {
            Shell.cmd("rm -f $BOOT_SCRIPT_PATH").exec()
            Log.d(TAG, "Boot script removed.")
        }
    }
}
