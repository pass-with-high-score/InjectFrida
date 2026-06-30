package app.pwhs.inject.frida.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import app.pwhs.inject.frida.ui.viewmodel.MainViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = koinViewModel()
) {
    val logs by viewModel.logs.collectAsState()
    val workState by viewModel.workState.collectAsState()
    val availableVersions by viewModel.availableVersions.collectAsState()
    val selectedVersion by viewModel.selectedVersion.collectAsState()

    var expanded by remember { mutableStateOf(false) }

    val isRunning = workState == WorkInfo.State.RUNNING || workState == WorkInfo.State.ENQUEUED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Frida Injector", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            Spacer(modifier = Modifier.height(24.dp))

            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Server Status",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val statusText = when (workState) {
                        WorkInfo.State.RUNNING -> "INJECTING..."
                        WorkInfo.State.ENQUEUED -> "WAITING..."
                        WorkInfo.State.SUCCEEDED -> "RUNNING"
                        WorkInfo.State.FAILED -> "FAILED"
                        else -> "STOPPED"
                    }
                    
                    val statusColor = when (workState) {
                        WorkInfo.State.RUNNING -> Color(0xFFE2B93B)
                        WorkInfo.State.SUCCEEDED -> Color(0xFF4CAF50)
                        WorkInfo.State.FAILED -> Color(0xFFF44336)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Version Selector
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(0.6f),
                    enabled = !isRunning && availableVersions.isNotEmpty()
                ) {
                    Text(text = selectedVersion ?: "Loading versions...")
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Version")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    availableVersions.forEach { version ->
                        DropdownMenuItem(
                            text = { Text(version) },
                            onClick = {
                                viewModel.selectVersion(version)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Main Action Button
            Button(
                onClick = { viewModel.startInjection() },
                modifier = Modifier
                    .size(160.dp),
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color.Gray else MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(40.dp) // Large rounded
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                } else {
                    Text(
                        text = "INJECT\nLATEST",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Terminal View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = false // Normally you want to auto-scroll to bottom, simple approach: just display top down for now
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            color = Color(0xFF4AF626), // Hacker green
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
