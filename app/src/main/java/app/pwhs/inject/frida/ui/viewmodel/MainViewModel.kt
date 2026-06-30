package app.pwhs.inject.frida.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.pwhs.inject.frida.data.local.SettingsManager
import app.pwhs.inject.frida.data.repository.GithubRepository
import app.pwhs.inject.frida.root.FridaRootManager
import app.pwhs.inject.frida.worker.DownloadAndInjectWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    application: Application,
    private val githubRepository: GithubRepository,
    private val settingsManager: SettingsManager,
    private val rootManager: FridaRootManager
) : AndroidViewModel(application) {

    private val workManager = WorkManager.getInstance(application)

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _workState = MutableStateFlow<WorkInfo.State?>(null)
    val workState: StateFlow<WorkInfo.State?> = _workState.asStateFlow()

    private val _availableVersions = MutableStateFlow<List<String>>(emptyList())
    val availableVersions: StateFlow<List<String>> = _availableVersions.asStateFlow()

    private val _selectedVersion = MutableStateFlow<String?>(null)
    val selectedVersion: StateFlow<String?> = _selectedVersion.asStateFlow()

    // Settings States
    private val _port = MutableStateFlow(settingsManager.getPort())
    val port: StateFlow<String> = _port.asStateFlow()

    private val _stealthMode = MutableStateFlow(settingsManager.isStealthMode())
    val stealthMode: StateFlow<Boolean> = _stealthMode.asStateFlow()

    private val _startOnBoot = MutableStateFlow(settingsManager.isStartOnBoot())
    val startOnBoot: StateFlow<Boolean> = _startOnBoot.asStateFlow()

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    init {
        addLog("System initialized. Ready to inject.")
        observeWork()
        fetchVersions()
        checkServerStatus()
    }

    private fun checkServerStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val running = rootManager.isFridaRunning()
            _isServerRunning.value = running
            if (running) {
                _workState.value = WorkInfo.State.SUCCEEDED
                addLog("Detected Frida server already running.")
            }
        }
    }

    private fun fetchVersions() {
        viewModelScope.launch {
            addLog("Fetching available Frida versions...")
            val versions = githubRepository.getAvailableVersions()
            if (versions.isNotEmpty()) {
                _availableVersions.value = versions
                _selectedVersion.value = versions.first() // Select latest by default
                addLog("Found ${versions.size} versions. Latest: ${versions.first()}")
            } else {
                addLog("Failed to fetch versions.")
            }
        }
    }

    fun selectVersion(version: String) {
        _selectedVersion.value = version
    }

    fun updatePort(newPort: String) {
        _port.value = newPort
        settingsManager.setPort(newPort)
    }

    fun toggleStealthMode(enabled: Boolean) {
        _stealthMode.value = enabled
        settingsManager.setStealthMode(enabled)
    }

    fun toggleStartOnBoot(enabled: Boolean) {
        _startOnBoot.value = enabled
        settingsManager.setStartOnBoot(enabled)
        viewModelScope.launch(Dispatchers.IO) {
            rootManager.toggleBootScript(enabled, _port.value, _stealthMode.value)
            addLog(if (enabled) "Start on Boot ENABLED" else "Start on Boot DISABLED")
        }
    }

    private fun observeWork() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkLiveData("FridaInjectWork").asFlow().collect { workInfos ->
                val workInfo = workInfos.firstOrNull()
                _workState.value = workInfo?.state

                when (workInfo?.state) {
                    WorkInfo.State.ENQUEUED -> addLog("Task enqueued...")
                    WorkInfo.State.RUNNING -> addLog("Task running: Downloading and injecting...")
                    WorkInfo.State.SUCCEEDED -> {
                        addLog("SUCCESS: Frida server injected and started!")
                        _isServerRunning.value = true
                        
                        // Re-apply boot script if enabled (in case stealth name changed)
                        if (_startOnBoot.value) {
                            withContext(Dispatchers.IO) {
                                rootManager.toggleBootScript(true, _port.value, _stealthMode.value)
                            }
                        }
                    }
                    WorkInfo.State.FAILED -> addLog("ERROR: Task failed. Check root access and internet connection.")
                    WorkInfo.State.CANCELLED -> addLog("Task cancelled.")
                    else -> {}
                }
            }
        }
    }

    fun startInjection() {
        val version = _selectedVersion.value
        
        addLog("------------------------------------")
        addLog("Starting injection process for version: ${version ?: "Latest"}...")
        addLog("Port: ${_port.value} | Stealth: ${_stealthMode.value}")
        
        val inputData = Data.Builder()
            .putString("TARGET_VERSION", version)
            .putString("PORT", _port.value)
            .putBoolean("STEALTH_MODE", _stealthMode.value)
            .build()
            
        val request = OneTimeWorkRequestBuilder<DownloadAndInjectWorker>()
            .setInputData(inputData)
            .build()

        workManager.enqueueUniqueWork(
            "FridaInjectWork",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun stopServer() {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("Stopping Frida server...")
            rootManager.stopFridaServer()
            _isServerRunning.value = false
            _workState.value = null
            addLog("Server stopped.")
        }
    }

    fun cleanUp() {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("Cleaning up temporary files...")
            rootManager.cleanUpFrida()
            _isServerRunning.value = false
            _workState.value = null
            addLog("Cleanup complete.")
        }
    }

    private fun addLog(message: String) {
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add("> $message")
        _logs.value = currentLogs
    }
}
