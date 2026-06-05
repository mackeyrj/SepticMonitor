package com.example.septicmonitor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.septicmonitor.ui.theme.SepticMonitorTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class SepticState(
    val distanceInches: Int = 0,
    val tankPercent: Int = 0,
    val statusText: String = "Unknown",
    val alarmText: String = "None",
    val lastReadingText: String = "Never",
    val pumpPowerStatus: String = "Offline",
    val pumpStatus: String = "Unknown",
    val pumpLastRun: String = "N/A",
    val pumpRunDuration: String = "N/A",
    val isWifiConnected: Boolean = false,
    val recentReadings: List<RecentReading> = emptyList(),
    val tankEmptyInches: Float = 55f,
    val tankFullInches: Float = 10f
)

class SepticViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SepticState())
    val uiState: StateFlow<SepticState> = _uiState.asStateFlow()

    private val database = FirebaseDatabase.getInstance("https://septicmonitor-61662-default-rtdb.firebaseio.com")
    private val statusRef = database.getReference("status")
    private val settingsRef = database.getReference("settings")
    private val logRef = database.getReference("pump_log")

    init {
        println("SepticMonitor: Initializing Firebase connection...")

        // Start listening to Firebase for real-time updates
        setupFirebaseListener()
        setupSettingsListener()
        setupLogListener()
    }

    private fun setupFirebaseListener() {
        statusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                println("SepticMonitor: Data changed! Snapshot: ${snapshot.value}")
                
                // This runs whenever you change data in the Firebase Console!
                val tankPercent = snapshot.child("tank_percent").getValue(Int::class.java)
                val distanceInches = snapshot.child("distance_inches").getValue(Int::class.java) ?: 0
                val pumpStatus = snapshot.child("pump_status").getValue(String::class.java) ?: "Idle"
                val pumpPower = snapshot.child("pump_power").getValue(String::class.java) ?: "Available"
                val pumpDuration = snapshot.child("pump_run_duration").getValue(String::class.java) ?: "N/A"

                if (tankPercent == null) {
                    println("SepticMonitor: tank_percent is null. Make sure it is inside 'status' folder.")
                    return
                }

                val newStatus = when {
                    tankPercent > 90 -> "CRITICAL"
                    tankPercent > 75 -> "High"
                    else -> "Normal"
                }

                _uiState.update { currentState ->
                    // Only add to history if the percentage actually changed
                    val isNewReading = currentState.tankPercent != tankPercent
                    
                    currentState.copy(
                        distanceInches = distanceInches,
                        tankPercent = tankPercent,
                        statusText = newStatus,
                        pumpStatus = pumpStatus,
                        pumpPowerStatus = pumpPower,
                        pumpRunDuration = pumpDuration,
                        lastReadingText = "Live from Cloud",
                        isWifiConnected = true,
                        recentReadings = if (isNewReading) {
                            (listOf(
                                RecentReading(
                                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()),
                                    "$tankPercent% full",
                                    newStatus
                                )
                            ) + currentState.recentReadings).take(5)
                        } else {
                            currentState.recentReadings
                        }
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {
                println("SepticMonitor: Firebase Error: ${error.message}")
            }
        })
    }

    private fun setupSettingsListener() {
        settingsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val empty = snapshot.child("tank_empty").getValue(Float::class.java) ?: 55f
                val full = snapshot.child("tank_full").getValue(Float::class.java) ?: 10f
                
                _uiState.update { it.copy(tankEmptyInches = empty, tankFullInches = full) }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun setupLogListener() {
        logRef.limitToLast(1).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Get the last timestamp from the pump log
                val lastRun = snapshot.children.firstOrNull()?.getValue(String::class.java) ?: "N/A"
                _uiState.update { it.copy(pumpLastRun = lastRun) }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun updateSettings(empty: Float, full: Float) {
        val settings = mapOf(
            "tank_empty" to empty,
            "tank_full" to full
        )
        settingsRef.setValue(settings)
    }

    fun refreshData() {
        // Clear any old flags and request a fresh reading
        statusRef.child("test_mode").setValue(false)
        statusRef.child("refresh_request").setValue(true)
        
        // Update UI immediately to show we are waiting
        _uiState.update { it.copy(lastReadingText = "Requesting live data...") }
        
        // Add a safety timeout: If no update in 12 seconds, reset the text
        viewModelScope.launch {
            delay(12000)
            if (_uiState.value.lastReadingText == "Requesting live data...") {
                _uiState.update { it.copy(lastReadingText = "Update Timeout (Check Hardware)") }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        checkNotificationPermission()
        scheduleDailyReport()

        setContent {
            val viewModel: SepticViewModel = viewModel()

            SepticMonitorTheme {
                SepticMonitorApp(viewModel)
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            scheduleDailyReport()
        }
    }

    private fun scheduleDailyReport() {
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24)
        }

        val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis

        val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyReportWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "DailyReportWork",
            ExistingPeriodicWorkPolicy.REPLACE,
            dailyWorkRequest
        )
    }
}

data class RecentReading(
    val time: String,
    val level: String,
    val status: String
)

@Composable
fun SepticMonitorApp(viewModel: SepticViewModel = viewModel()) {
    val context = LocalContext.current
    val intent = (context as? MainActivity)?.intent
    val isFromNotification = intent?.getBooleanExtra("OPEN_REPORT", false) == true
    
    var showCustomSplash by remember { mutableStateOf(!isFromNotification) }
    var currentScreen by remember { mutableStateOf("dashboard") }
    val uiState by viewModel.uiState.collectAsState()

    // Handle the notification report logic
    LaunchedEffect(isFromNotification) {
        if (isFromNotification) {
            println("SepticMonitor: Notification detected. Skipping splash and waiting for cloud data...")
            
            // Wait up to 10 seconds for real cloud data to arrive
            val finalState = withTimeoutOrNull(10000) {
                viewModel.uiState.first { it.lastReadingText == "Live from Cloud" }
            }

            if (finalState != null) {
                emailDailyReport(
                    context = context,
                    tankPercent = finalState.tankPercent,
                    distanceInches = finalState.distanceInches,
                    statusText = finalState.statusText,
                    alarmText = finalState.alarmText,
                    lastReadingText = finalState.lastReadingText,
                    pumpPowerStatus = finalState.pumpPowerStatus,
                    pumpStatus = finalState.pumpStatus,
                    pumpLastRun = finalState.pumpLastRun,
                    pumpRunDuration = finalState.pumpRunDuration,
                    recentReadings = finalState.recentReadings
                )
            } else {
                // If cloud is slow, send what we have but mark it as pending
                emailDailyReport(
                    context = context,
                    tankPercent = uiState.tankPercent,
                    distanceInches = uiState.distanceInches,
                    statusText = uiState.statusText,
                    alarmText = uiState.alarmText,
                    lastReadingText = uiState.lastReadingText + " (Connecting...)",
                    pumpPowerStatus = uiState.pumpPowerStatus,
                    pumpStatus = uiState.pumpStatus,
                    pumpLastRun = uiState.pumpLastRun,
                    pumpRunDuration = uiState.pumpRunDuration,
                    recentReadings = uiState.recentReadings
                )
            }
            // Consume the intent so it doesn't fire again
            intent.removeExtra("OPEN_REPORT")
        }
    }

    LaunchedEffect(Unit) {
        if (!isFromNotification) {
            delay(12000)
            showCustomSplash = false
        }
    }

    if (showCustomSplash) {
        CustomSplashScreen(
            onSkip = { showCustomSplash = false }
        )
    } else {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF101820)
        ) {
            when (currentScreen) {
                "dashboard" -> SepticDashboard(
                    state = uiState,
                    onRefresh = { viewModel.refreshData() },
                    onOpenSettings = { currentScreen = "settings" }
                )
                "settings" -> SettingsScreen(
                    state = uiState,
                    onBack = { currentScreen = "dashboard" },
                    onSave = { empty, full ->
                        viewModel.updateSettings(empty, full)
                        currentScreen = "dashboard"
                    }
                )
            }
        }
    }
}

@Composable
fun CustomSplashScreen(
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101820))
            .clickable { onSkip() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.septic_monitor_icon_full),
                contentDescription = "Septic Monitor Splash Image",
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .sizeIn(maxWidth = 320.dp, maxHeight = 320.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Septic Monitor",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tank Status & Monitoring",
                fontSize = 16.sp,
                color = Color(0xFFB8C7D9),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Tap anywhere to continue",
                fontSize = 13.sp,
                color = Color(0xFF7F95A8),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SepticDashboard(
    state: SepticState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (state.isWifiConnected) "Wi-Fi connected • Last reading: ${state.lastReadingText}" 
                       else "Offline • Last reading: ${state.lastReadingText}",
                fontSize = 15.sp,
                color = Color(0xFFB8C7D9),
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
        }

        val statusColor = when (state.statusText) {
            "CRITICAL" -> Color(0xFFE74C3C) // Red
            "High" -> Color(0xFFF1C40F)     // Yellow
            else -> Color(0xFF2ECC71)       // Green
        }

        val statusDetail = when (state.statusText) {
            "CRITICAL" -> "Immediate action required!"
            "High" -> "Tank is filling up. Monitor closely."
            else -> "Tank level is within the safe range"
        }

        StatusCard(
            title = "System Status",
            mainValue = state.statusText,
            detail = statusDetail,
            color = statusColor
        )

        TankLevelCard(
            tankPercent = state.tankPercent,
            distanceInches = state.distanceInches
        )

        PumpPowerMonitorCard(
            pumpPowerStatus = state.pumpPowerStatus,
            pumpStatus = state.pumpStatus,
            pumpLastRun = state.pumpLastRun,
            pumpRunDuration = state.pumpRunDuration,
            onRefresh = onRefresh
        )

        AlertCard(
            alarmText = state.alarmText
        )

        HistoryCard(
            tankPercent = state.tankPercent,
            distanceInches = state.distanceInches,
            statusText = state.statusText,
            alarmText = state.alarmText,
            lastReadingText = state.lastReadingText,
            pumpPowerStatus = state.pumpPowerStatus,
            pumpStatus = state.pumpStatus,
            pumpLastRun = state.pumpLastRun,
            pumpRunDuration = state.pumpRunDuration,
            recentReadings = state.recentReadings
        )
    }
}

@Composable
fun StatusCard(
    title: String,
    mainValue: String,
    detail: String,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B2A38)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(18.dp)
                    .background(color, RoundedCornerShape(50))
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = title,
                    color = Color(0xFFB8C7D9),
                    fontSize = 14.sp
                )

                Text(
                    text = mainValue,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = detail,
                    color = Color(0xFFB8C7D9),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TankLevelCard(
    tankPercent: Int,
    distanceInches: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B2A38)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tank Level",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "$tankPercent% full",
                color = Color(0xFF4FC3F7),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )

            LinearProgressIndicator(
                progress = { tankPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                color = Color(0xFF4FC3F7),
                trackColor = Color(0xFF34495E)
            )

            Text(
                text = "Sensor distance to liquid: $distanceInches inches",
                color = Color(0xFFB8C7D9),
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun PumpPowerMonitorCard(
    pumpPowerStatus: String,
    pumpStatus: String,
    pumpLastRun: String,
    pumpRunDuration: String,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B2A38)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Pump Power Monitor",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Smart plug is used for monitoring only",
                        color = Color(0xFFB8C7D9),
                        fontSize = 14.sp
                    )
                }

                StatusPill(
                    text = pumpPowerStatus.uppercase(),
                    color = if (pumpPowerStatus == "Online") Color(0xFF2ECC71) else Color(0xFFE74C3C)
                )
            }

            PumpInfoRow(
                label = "Power",
                value = pumpPowerStatus
            )

            PumpInfoRow(
                label = "Pump",
                value = pumpStatus
            )

            PumpInfoRow(
                label = "Last run",
                value = pumpLastRun
            )

            PumpInfoRow(
                label = "Run duration",
                value = pumpRunDuration
            )

            Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh Reading")
            }

            Text(
                text = "Leave the pump circuit powered for normal operation.",
                color = Color(0xFFFFD54F),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun PumpInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color(0xFFB8C7D9),
            fontSize = 15.sp
        )

        Text(
            text = value,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.18f),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AlertCard(
    alarmText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF263238)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = "Alarm",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = alarmText,
                color = Color(0xFFB8C7D9),
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun HistoryCard(
    tankPercent: Int,
    distanceInches: Int,
    statusText: String,
    alarmText: String,
    lastReadingText: String,
    pumpPowerStatus: String,
    pumpStatus: String,
    pumpLastRun: String,
    pumpRunDuration: String,
    recentReadings: List<RecentReading>
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B2A38)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Recent Readings",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            recentReadings.forEach { reading ->
                HistoryRow(reading.time, reading.level, reading.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    emailDailyReport(
                        context = context,
                        tankPercent = tankPercent,
                        distanceInches = distanceInches,
                        statusText = statusText,
                        alarmText = alarmText,
                        lastReadingText = lastReadingText,
                        pumpPowerStatus = pumpPowerStatus,
                        pumpStatus = pumpStatus,
                        pumpLastRun = pumpLastRun,
                        pumpRunDuration = pumpRunDuration,
                        recentReadings = recentReadings
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Email Today's Report")
            }
        }
    }
}

@Composable
fun HistoryRow(
    time: String,
    level: String,
    status: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = time,
            color = Color(0xFFB8C7D9),
            fontSize = 14.sp
        )

        Text(
            text = level,
            color = Color.White,
            fontSize = 14.sp
        )

        Text(
            text = status,
            color = Color(0xFF2ECC71),
            fontSize = 14.sp
        )
    }
}

fun emailDailyReport(
    context: Context,
    tankPercent: Int,
    distanceInches: Int,
    statusText: String,
    alarmText: String,
    lastReadingText: String,
    pumpPowerStatus: String,
    pumpStatus: String,
    pumpLastRun: String,
    pumpRunDuration: String,
    recentReadings: List<RecentReading>
) {
    val reportDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
    val reportGeneratedAt = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date())
    val subject = "Septic Monitor Daily Report - $reportDate"
    val body = buildDailyReport(
        reportDate = reportDate,
        reportGeneratedAt = reportGeneratedAt,
        tankPercent = tankPercent,
        distanceInches = distanceInches,
        statusText = statusText,
        alarmText = alarmText,
        lastReadingText = lastReadingText,
        pumpPowerStatus = pumpPowerStatus,
        pumpStatus = pumpStatus,
        pumpLastRun = pumpLastRun,
        pumpRunDuration = pumpRunDuration,
        recentReadings = recentReadings
    )

    // Using a more robust Intent selector to ensure text is passed correctly to Gmail
    val selectorIntent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
    }
    
    val emailIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf("mackeyrj@gmail.com"))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        selector = selectorIntent
    }

    context.startActivity(Intent.createChooser(emailIntent, "Email Daily Report"))
}

fun buildDailyReport(
    reportDate: String,
    reportGeneratedAt: String,
    tankPercent: Int,
    distanceInches: Int,
    statusText: String,
    alarmText: String,
    lastReadingText: String,
    pumpPowerStatus: String,
    pumpStatus: String,
    pumpLastRun: String,
    pumpRunDuration: String,
    recentReadings: List<RecentReading>
): String {
    val readingsText = recentReadings.joinToString(separator = "\n") { reading ->
        "- ${reading.time}: ${reading.level}, ${reading.status}"
    }

    return """
        Septic Monitor Daily Report
        Date: $reportDate
        Generated: $reportGeneratedAt

        System Status
        - Status: $statusText
        - Alarm: $alarmText
        - Last reading: $lastReadingText

        Tank Level
        - Tank level: $tankPercent% full
        - Sensor distance to liquid: $distanceInches inches

        Pump Power Monitor
        - Power: $pumpPowerStatus
        - Pump: $pumpStatus
        - Last run: $pumpLastRun
        - Run duration: $pumpRunDuration

        Today's Recent Readings
        $readingsText
    """.trimIndent()
}

@Composable
fun SettingsScreen(
    state: SepticState,
    onBack: () -> Unit,
    onSave: (Float, Float) -> Unit
) {
    var tankEmpty by remember { mutableStateOf(state.tankEmptyInches.toString()) }
    var tankFull by remember { mutableStateOf(state.tankFullInches.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = "Calibration Settings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2A38)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Tank Dimensions",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = tankEmpty,
                    onValueChange = { tankEmpty = it },
                    label = { Text("Tank Empty (inches)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF4FC3F7),
                        unfocusedBorderColor = Color(0xFF34495E),
                        focusedLabelColor = Color(0xFF4FC3F7),
                        unfocusedLabelColor = Color(0xFFB8C7D9)
                    )
                )

                OutlinedTextField(
                    value = tankFull,
                    onValueChange = { tankFull = it },
                    label = { Text("Tank Full (inches)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF4FC3F7),
                        unfocusedBorderColor = Color(0xFF34495E),
                        focusedLabelColor = Color(0xFF4FC3F7),
                        unfocusedLabelColor = Color(0xFFB8C7D9)
                    )
                )

                Text(
                    text = "Empty: Distance from sensor to bottom of tank.\nFull: Distance from sensor to max fill line.",
                    color = Color(0xFFB8C7D9),
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                val empty = tankEmpty.toFloatOrNull() ?: state.tankEmptyInches
                val full = tankFull.toFloatOrNull() ?: state.tankFullInches
                onSave(empty, full)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Save Calibration", fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}
