package com.example.septicmonitor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val recentReadings: List<RecentReading> = emptyList()
)

class SepticViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SepticState())
    val uiState: StateFlow<SepticState> = _uiState.asStateFlow()

    init {
        // Initial mock data - later we will fetch this from ESP32/Cloud
        refreshData()
    }

    fun refreshData() {
        _uiState.update { currentState ->
            // Simulating a reading change
            val newPercent = (currentState.tankPercent + 5).let { if (it > 100) 0 else it }
            val newDistance = 60 - (newPercent * 0.4).toInt() // Simulate distance decreasing as tank fills
            
            val newStatus = when {
                newPercent > 90 -> "CRITICAL"
                newPercent > 75 -> "High"
                else -> "Normal"
            }

            currentState.copy(
                distanceInches = newDistance,
                tankPercent = newPercent,
                statusText = newStatus,
                lastReadingText = "Just now (Simulated)",
                pumpPowerStatus = if (newPercent > 50) "Active" else "Available",
                pumpStatus = if (newPercent > 50) "Pumping..." else "Idle",
                isWifiConnected = true,
                recentReadings = listOf(
                    RecentReading("Now", "$newPercent% full", newStatus)
                ) + currentState.recentReadings.take(4)
            )
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            SepticMonitorTheme {
                SepticMonitorApp()
            }
        }
    }
}

data class RecentReading(
    val time: String,
    val level: String,
    val status: String
)

@Composable
fun SepticMonitorApp(viewModel: SepticViewModel = viewModel()) {
    var showCustomSplash by remember { mutableStateOf(true) }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        delay(12000)
        showCustomSplash = false
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
            SepticDashboard(uiState, onRefresh = { viewModel.refreshData() })
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
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (state.isWifiConnected) "Wi-Fi connected • Last reading: ${state.lastReadingText}" 
                   else "Offline • Last reading: ${state.lastReadingText}",
            fontSize = 15.sp,
            color = Color(0xFFB8C7D9)
        )

        StatusCard(
            title = "System Status",
            mainValue = state.statusText,
            detail = "Tank level is within the safe range",
            color = Color(0xFF2ECC71)
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
                    text = "ONLINE",
                    color = Color(0xFF2ECC71)
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

    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
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
