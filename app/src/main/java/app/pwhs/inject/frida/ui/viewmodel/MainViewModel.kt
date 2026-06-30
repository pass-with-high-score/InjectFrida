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
import app.pwhs.inject.frida.data.repository.GithubRepository
import app.pwhs.inject.frida.worker.DownloadAndInjectWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
    private val githubRepository: GithubRepository
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

    init {
        addLog("System initialized. Ready to inject.")
        observeWork()
        fetchVersions()
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

    private fun observeWork() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkLiveData("FridaInjectWork").asFlow().collect { workInfos ->
                val workInfo = workInfos.firstOrNull()
                _workState.value = workInfo?.state

                when (workInfo?.state) {
                    WorkInfo.State.ENQUEUED -> addLog("Task enqueued...")
                    WorkInfo.State.RUNNING -> addLog("Task running: Downloading and injecting...")
                    WorkInfo.State.SUCCEEDED -> addLog("SUCCESS: Frida server injected and started!")
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
        
        val inputData = Data.Builder()
            .putString("TARGET_VERSION", version)
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

    private fun addLog(message: String) {
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add("> $message")
        _logs.value = currentLogs
    }
}
