#include <Arduino.h> // Force Arduino core first
#include <WiFi.h>
#include <FirebaseESP32.h>
#include "secrets.h"

// SYNC TEST: If you see this in your Arduino IDE, the sync is working!
// You can now hit "Upload" in Arduino and "Commit" in Android Studio.

// Firebase Objects
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

// Simulation variables
int mockPercent = 0;
unsigned long lastUpdate = 0;
const long interval = 5000; // Update every 5 seconds

void setup() {
  Serial.begin(115200);

  // 1. Connect to Wi-Fi
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting to Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nConnected with IP: " + WiFi.localIP().toString());

  // 2. Configure Firebase
  config.host = FIREBASE_HOST;
  config.signer.tokens.legacy_token = FIREBASE_AUTH;

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);
  
  Serial.println("Firebase Connection Initialized");
}

void loop() {
  // Only update every 'interval' milliseconds
  if (millis() - lastUpdate > interval) {
    lastUpdate = millis();

    // 3. Simulated Sensor Logic (while waiting for hardware)
    mockPercent += 2;
    if (mockPercent > 100) mockPercent = 0;

    int mockDistance = 60 - (mockPercent * 0.4); // Simulate distance decreasing

    // 4. Send Data to Firebase
    // These paths match exactly what your SepticViewModel is looking for!
    if (Firebase.ready()) {
      Firebase.setInt(fbdo, "/status/tank_percent", mockPercent);
      Firebase.setInt(fbdo, "/status/distance_inches", mockDistance);
      Firebase.setString(fbdo, "/status/pump_status", (mockPercent > 80) ? "Pumping..." : "Idle");
      Firebase.setString(fbdo, "/status/pump_power", "Available");
      
      Serial.printf("Pushed to Cloud: %d%% full\n", mockPercent);
    } else {
      Serial.println("Firebase Error: " + fbdo.errorReason());
    }
  }
}