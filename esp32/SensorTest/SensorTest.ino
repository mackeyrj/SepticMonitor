#include <Arduino.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <FirebaseESP32.h>
#include "time.h"
#include "secrets.h"

/*
  SEPTIC MONITOR - FINAL INTEGRATED FIRMWARE
  - Sensor: A02YYUW (D0/D1)
  - Pump Monitor: Shelly Plus (192.168.86.43)
  - Cloud: Firebase Realtime Database
*/

// Configuration
const char* shellyIP = "192.168.86.43";
float tankEmptyInches = 55.0;  // Default
float tankFullInches = 10.0;   // Default
const char* ntpServer = "pool.ntp.org";

// Firebase Objects
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

// Global State
bool wasPumping = false;
unsigned long pumpStartTime = 0;
unsigned long lastDistanceUpdate = 0;
unsigned long distanceInterval = 15000;  // Start in 15s Test Mode
int heartbeatCount = 0;

void setup() {
  Serial.begin(115200);
  Serial1.begin(9600, SERIAL_8N1, D0, D1);

  // Initial Wi-Fi Setup
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting to Wi-Fi");
  int retry = 0;
  while (WiFi.status() != WL_CONNECTED && retry < 20) {
    delay(500);
    Serial.print(".");
    retry++;
  }
  Serial.println("\nWiFi Setup Complete.");

  configTime(-18000, 3600, ntpServer);

  config.host = FIREBASE_HOST;
  config.signer.tokens.legacy_token = FIREBASE_AUTH;
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  // Fetch initial settings
  if (Firebase.ready()) {
    if (Firebase.getFloat(fbdo, "/settings/tank_empty")) {
      tankEmptyInches = fbdo.floatData();
    }
    if (Firebase.getFloat(fbdo, "/settings/tank_full")) {
      tankFullInches = fbdo.floatData();
    }
    Serial.printf("Settings Loaded: Empty: %.1f, Full: %.1f\n", tankEmptyInches, tankFullInches);
  }
}

int getDistanceMM() {
  while (Serial1.available()) Serial1.read();
  delay(20);
  Serial1.write(0x55);
  Serial1.flush();
  unsigned long start = millis();
  while (millis() - start < 300) {
    if (Serial1.available() >= 4) {
      if (Serial1.read() == 0xFF) {
        uint8_t h = Serial1.read();
        uint8_t l = Serial1.read();
        uint8_t sum = Serial1.read();
        if (((0xFF + h + l) & 0xFF) == sum) return (h << 8) | l;
      }
    }
  }
  return -1;
}

float getShellyPower() {
  HTTPClient http;
  http.setTimeout(3000);  // 3-second limit so it can't hang the loop
  http.begin("http://" + String(shellyIP) + "/rpc/Switch.GetStatus?id=0");
  int httpCode = http.GET();
  float watts = -1.0;  // -1 indicates error/offline
  if (httpCode == 200) {
    StaticJsonDocument<512> doc;
    deserializeJson(doc, http.getString());
    watts = doc["apower"];
  }
  http.end();
  return watts;
}

String getTimeString() {
  struct tm timeinfo;
  if (!getLocalTime(&timeinfo)) return "UnknownTime";
  char timeStr[20];
  strftime(timeStr, sizeof(timeStr), "%Y-%m-%d %H:%M:%S", &timeinfo);
  return String(timeStr);
}

void loop() {
  // 1. Wi-Fi Watchdog - Ensure we are ALWAYS connected
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("Wi-Fi Lost! Reconnecting...");
    WiFi.disconnect();
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    delay(1000);
    return;  // Skip this loop and wait for Wi-Fi
  }

  // 2. Check Pump (Fast check - every 5 seconds)
  float watts = getShellyPower();
  bool isPumping = (watts > 10.0);

  if (isPumping && !wasPumping) {
    wasPumping = true;
    pumpStartTime = millis();
    Firebase.pushString(fbdo, "/pump_log", getTimeString());
    Firebase.setString(fbdo, "/status/pump_status", "Pumping...");
  } else if (!isPumping && wasPumping) {
    wasPumping = false;
    unsigned long durationSeconds = (millis() - pumpStartTime) / 1000;

    String durationText;
    if (durationSeconds < 60) {
      durationText = String(durationSeconds) + " sec";
    } else {
      durationText = String(durationSeconds / 60) + " min " + String(durationSeconds % 60) + " sec";
    }

    Firebase.setString(fbdo, "/status/pump_run_duration", durationText);
    Firebase.setString(fbdo, "/status/pump_status", "Idle");
  }

  // 3. Check Distance
  bool forceRefresh = false;
  if (Firebase.ready()) {
    // Check if the app requested an immediate refresh
    if (Firebase.getBool(fbdo, "/status/refresh_request") && fbdo.boolData()) {
      forceRefresh = true;
      Firebase.setBool(fbdo, "/status/refresh_request", false);  // Clear the flag
    }

    // Check if we should quit Test Mode
    if (Firebase.getBool(fbdo, "/status/test_mode")) {
      bool isTest = fbdo.boolData();
      distanceInterval = isTest ? 15000 : 30000;
    }
  }

  if (millis() - lastDistanceUpdate > distanceInterval || lastDistanceUpdate == 0 || forceRefresh) {
    lastDistanceUpdate = millis();
    int distMM = getDistanceMM();

    if (distMM > 0 && Firebase.ready()) {
      float inches = distMM / 25.4;

      // Periodically refresh settings (every 10 minutes)
      static unsigned long lastSettingsRefresh = 0;
      if (millis() - lastSettingsRefresh > 600000) {
        lastSettingsRefresh = millis();
        if (Firebase.getFloat(fbdo, "/settings/tank_empty")) tankEmptyInches = fbdo.floatData();
        if (Firebase.getFloat(fbdo, "/settings/tank_full")) tankFullInches = fbdo.floatData();
      }

      // Handle Daily Heartbeat Reset
      struct tm timeinfo;
      if (getLocalTime(&timeinfo)) {
        static int lastDay = -1;
        if (timeinfo.tm_mday != lastDay) {
          heartbeatCount = 0;  // Reset at midnight
          lastDay = timeinfo.tm_mday;
        }
      }

      heartbeatCount++;

      int currentPercent = constrain(map(inches * 10, tankEmptyInches * 10, tankFullInches * 10, 0, 100), 0, 100);

      // BUNDLED UPDATE: Send all data at once
      FirebaseJson json;
      json.set("tank_percent", currentPercent);
      json.set("distance_inches", (int)inches);
      json.set("heartbeat", heartbeatCount);

      Firebase.updateNode(fbdo, "/status", json);

      Serial.printf("LOGGED: Dist: %d in | Pump: %s | Heartbeat: %d\n", (int)inches, isPumping ? "ON" : "OFF", heartbeatCount);
    }
  }

  // 4. Always report pump power status
  if (Firebase.ready()) {
    String pumpPower = (watts >= 0.0) ? "Online" : "Offline";
    Firebase.setString(fbdo, "/status/pump_power", pumpPower);
  }

  delay(5000);  // Check the pump every 5 seconds
}
