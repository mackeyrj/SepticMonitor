package com.example.septicmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            SepticMonitorApp()
        }
    }
}

@Composable
fun SepticMonitorApp() {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(20000)
        showSplash = false
    }

    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        if (showSplash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF101820))
                    .clickable { showSplash = false },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "Splash Logo",
                    modifier = Modifier.size(200.dp)
                )
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF101820)
            ) {
                SepticDashboard()
            }
        }
    }
}

@Composable
fun SepticDashboard() {
    var pumpEnabled by remember { mutableStateOf(false) }

    val distanceInches = 42
    val tankPercent = 63
    val statusText = "Normal"
    val alarmText = "No alarm"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Septic Monitor",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Text(
            text = "Wi-Fi connected • Last reading: 2 minutes ago",
            fontSize = 15.sp,
            color = Color(0xFFB8C7D9)
        )

        StatusCard(
            title = "System Status",
            mainValue = statusText,
            detail = "Tank level is within the safe range",
            color = Color(0xFF2ECC71)
        )

        TankLevelCard(
            tankPercent = tankPercent,
            distanceInches = distanceInches
        )

        PumpControlCard(
            pumpEnabled = pumpEnabled,
            onPumpChanged = { pumpEnabled = it }
        )

        AlertCard(
            alarmText = alarmText
        )

        HistoryCard()
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
                progress = tankPercent / 100f,
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
fun PumpControlCard(
    pumpEnabled: Boolean,
    onPumpChanged: (Boolean) -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Pump Control",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (pumpEnabled) "Pump smart plug is ON" else "Pump smart plug is OFF",
                        color = Color(0xFFB8C7D9),
                        fontSize = 15.sp
                    )
                }

                Switch(
                    checked = pumpEnabled,
                    onCheckedChange = onPumpChanged
                )
            }

            Button(
                onClick = {
                    // Later this will request a fresh reading from the ESP32.
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh Reading")
            }
        }
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
fun HistoryCard() {
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

            HistoryRow("9:12 AM", "63% full", "Normal")
            HistoryRow("8:12 AM", "62% full", "Normal")
            HistoryRow("7:12 AM", "61% full", "Normal")
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