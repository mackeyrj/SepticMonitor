#include <Arduino.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <FirebaseESP32.h> // Matching your installed library

#include "secrets.h"

/*
  FINAL INTEGRATED FIRMWARE (FirebaseESP32 version)
  - Sensor: A02YYUW (D0/D1)
  - Pump Monitor: Shelly Plus (192.168.86.43)
*/

// Configuration
const char* shellyIP = "192.168.86.43";
const float TANK_EMPTY_INCHES = 55.0;
const float TANK_FULL_INCHES = 10.0;

// Firebase Objects
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

void setup() {
  Serial.begin(115200);

  // Initialize Sensor Serial
  Serial1.begin(9600, SERIAL_8N1, D0, D1);

  // Connect to Wi-Fi
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting to Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi Connected!");

  // Configure Firebase
  config.host = FIREBASE_HOST;
  config.signer.tokens.legacy_token = FIREBASE_AUTH;
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);
}

// Function to get distance from A02YYUW
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
        if (((0xFF + h + l) & 0xFF) == sum) {
          return (h << 8) | l;
        }
      }
    }
  }
  return -1;
}

// Function to check Shelly Power
float getShellyPower() {
  HTTPClient http;
  String url = "http://" + String(shellyIP) + "/rpc/Switch.GetStatus?id=0";
  http.begin(url);
  int httpCode = http.GET();
  float watts = 0;
  if (httpCode == 200) {
    String payload = http.getString();
    StaticJsonDocument<512> doc;
    deserializeJson(doc, payload);
    watts = doc["apower"];
  }
  http.end();
  return watts;
}

void loop() {
  // 1. Get Sensor Data
  int distMM = getDistanceMM();
  int currentPercent = -1;
  int distInches = 0;

  if (distMM > 0) {
    float inches = distMM / 25.4;
    distInches = (int)inches;
    currentPercent = map(inches * 10, TANK_EMPTY_INCHES * 10, TANK_FULL_INCHES * 10, 0, 100);
    currentPercent = constrain(currentPercent, 0, 100);
  }

  // 2. Get Pump Status from Shelly
  float watts = getShellyPower();
  String pumpStatus = (watts > 5.0) ? "Pumping..." : "Idle";
  String pumpPower = (watts > 0.1) ? "Available" : "Offline";

  // 3. Update Firebase
  if (Firebase.ready() && currentPercent != -1) {
    Firebase.setInt(fbdo, "/status/tank_percent", currentPercent);
    Firebase.setInt(fbdo, "/status/distance_inches", distInches);
    Firebase.setString(fbdo, "/status/pump_status", pumpStatus);
    Firebase.setString(fbdo, "/status/pump_power", pumpPower);

    Serial.printf("Tank: %d%% | Pump: %s (%.1fW)\n", currentPercent, pumpStatus.c_str(), watts);
  }

  delay(5000);
}
