#include <Arduino.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <FirebaseESP32.h>
#include "time.h" // For NTP time
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
int pumpRunCount = 0;

void setup() {
  Serial.begin(115200);
  Serial1.begin(9600, SERIAL_8N1, D0, D1);

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) { delay(500); Serial.print("."); }
  Serial.println("\nWiFi Connected!");

  // Initialize Time (NTP) - Set your offset (e.g., -5 for EST)
  configTime(-18000, 3600, ntpServer); // -5 hours * 3600 seconds

  config.host = FIREBASE_HOST;
  config.signer.tokens.legacy_token = FIREBASE_AUTH;
  Firebase.begin(&config, &auth);
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
  int distMM = getDistanceMM();
  float watts = getShellyPower();
  bool isPumping = (watts > 10.0); // Adjust threshold for your pump

  // Pump Edge Detection (Detecting when it STARTS)
  if (isPumping && !wasPumping) {
    wasPumping = true;
    String currentTime = getTimeString();
    Serial.println("PUMP STARTED! Logging to Firebase...");
    // Push a new entry to the pump log folder
    Firebase.pushString(fbdo, "/pump_log", currentTime);
  } else if (!isPumping && wasPumping) {
    wasPumping = false;
    Serial.println("Pump Stopped.");
  }

  // Standard updates
  if (Firebase.ready()) {
    if (distMM > 0) {
        float inches = distMM / 25.4;
        int currentPercent = constrain(map(inches * 10, TANK_EMPTY_INCHES * 10, TANK_FULL_INCHES * 10, 0, 100), 0, 100);
        Firebase.setInt(fbdo, "/status/tank_percent", currentPercent);
        Firebase.setInt(fbdo, "/status/distance_inches", (int)inches);
    }
    Firebase.setString(fbdo, "/status/pump_status", isPumping ? "Pumping..." : "Idle");
  }

  delay(5000);
}
