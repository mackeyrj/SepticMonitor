#include <Arduino.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <FirebaseESP32.h>
#include "time.h"
#include "secrets.h"

// Configuration
const char* shellyIP = "192.168.86.43";
const float TANK_EMPTY_INCHES = 55.0;
const float TANK_FULL_INCHES = 10.0;
const char* ntpServer = "pool.ntp.org";

// Firebase Objects
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

// Global State
bool wasPumping = false;
unsigned long lastDistanceUpdate = 0;
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
  http.setTimeout(3000); // 3-second limit so it can't hang the loop
  http.begin("http://" + String(shellyIP) + "/rpc/Switch.GetStatus?id=0");
  int httpCode = http.GET();
  float watts = 0;
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
  if(!getLocalTime(&timeinfo)) return "UnknownTime";
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
    return; // Skip this loop and wait for Wi-Fi
  }

  // 2. Check Pump (Fast check - every 5 seconds)
  float watts = getShellyPower();
  bool isPumping = (watts > 10.0);

  if (isPumping && !wasPumping) {
    wasPumping = true;
    Firebase.pushString(fbdo, "/pump_log", getTimeString());
    Firebase.setString(fbdo, "/status/pump_status", "Pumping...");
  } else if (!isPumping && wasPumping) {
    wasPumping = false;
    Firebase.setString(fbdo, "/status/pump_status", "Idle");
  }

  // 3. Check Distance (Slow check - every 60 seconds)
  if (millis() - lastDistanceUpdate > 60000 || lastDistanceUpdate == 0) {
    lastDistanceUpdate = millis();
    int distMM = getDistanceMM();

    if (distMM > 0 && Firebase.ready()) {
        float inches = distMM / 25.4;
        int currentPercent = constrain(map(inches * 10, TANK_EMPTY_INCHES * 10, TANK_FULL_INCHES * 10, 0, 100), 0, 100);

        heartbeatCount++;

        // BUNDLED UPDATE: Send all data at once
        FirebaseJson json;
        json.set("tank_percent", currentPercent);
        json.set("distance_inches", (int)inches);
        json.set("heartbeat", heartbeatCount);

        Firebase.updateNode(fbdo, "/status", json);

        Serial.printf("LOGGED: Dist: %d in | Pump: %s | Heartbeat: %d\n", (int)inches, isPumping ? "ON" : "OFF", heartbeatCount);
    }
  }

  delay(5000); // Check the pump every 5 seconds
}
