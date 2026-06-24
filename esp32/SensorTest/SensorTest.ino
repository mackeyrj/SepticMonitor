#include <Arduino.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <FirebaseESP32.h>
#include <TFT_eSPI.h> // LilyGo T-Display-S3 Screen Library
#include "time.h"
#include "secrets.h"

/*
  SEPTIC MONITOR - LILYGO T-DISPLAY-S3 EDITION
  - Controller: ESP32-S3
  - Display: Built-in 1.9" LCD
  - Sensor: A02YYUW (Pins 17 TX / 18 RX)
  - Pump Monitor: Shelly Plus (192.168.86.43)
*/

// --- LILYGO S3 PIN MAPPING ---
#define SENSOR_TX 17
#define SENSOR_RX 18

// Configuration
const char* shellyIP = "192.168.86.43";
float tankEmptyInches = 55.0;
float tankFullInches = 10.0;
const char* ntpServer = "pool.ntp.org";

// Firebase Objects
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;
TFT_eSPI tft = TFT_eSPI(); // Initialize the Screen

// Global State
bool wasPumping = false;
unsigned long pumpStartTime = 0;
unsigned long lastDistanceUpdate = 0;
unsigned long distanceInterval = 15000;
int heartbeatCount = 0;
float currentWatts = 0.0;
int lastPercent = 0;
int lastInches = 0;

void updateDisplay() {
  tft.fillScreen(TFT_BLACK);
  tft.setTextColor(TFT_WHITE, TFT_BLACK);

  // 1. Header (Lowered to avoid bezel)
  tft.setTextSize(2);
  tft.setCursor(0, 5);
  tft.println(" SEPTIC MONITOR S3");
  tft.drawFastHLine(0, 25, 320, TFT_BLUE);

  // 2. Tank Level Section
  tft.setTextSize(3);
  tft.setCursor(10, 40);
  tft.printf("%d%% FULL", lastPercent);

  // Draw the Progress Bar (Compact version)
  tft.drawRect(10, 75, 180, 25, TFT_WHITE);
  int barWidth = map(lastPercent, 0, 100, 0, 176);
  uint16_t barColor = (lastPercent > 80) ? TFT_RED : (lastPercent > 60) ? TFT_YELLOW : TFT_GREEN;
  if (barWidth > 0) {
    tft.fillRect(12, 77, barWidth, 21, barColor);
  }

  // 3. Status Info (Shifted Up)
  tft.setTextSize(2);
  tft.setCursor(10, 110);
  tft.printf("Dist: %d in", lastInches);

  tft.setCursor(10, 135);
  tft.print("Pump: ");
  if (currentWatts > 10.0) {
    tft.setTextColor(TFT_RED, TFT_BLACK);
    tft.print("RUNNING");
  } else {
    tft.setTextColor(TFT_GREEN, TFT_BLACK);
    tft.print("IDLE");
  }
  tft.setTextColor(TFT_WHITE, TFT_BLACK);

  // 4. Status Bar (Raised so it's visible)
  tft.setTextSize(1);
  tft.setCursor(5, 160);
  tft.drawFastHLine(0, 158, 320, TFT_DARKGREY);
  tft.printf("WiFi: %s | HB: %d", (WiFi.status() == WL_CONNECTED ? "OK" : "LOSS"), heartbeatCount);
}

void setup() {
  // --- MANDATORY LILYGO S3 POWER ON ---
  pinMode(15, OUTPUT);
  digitalWrite(15, HIGH); // Turn on screen power
  pinMode(38, OUTPUT);
  digitalWrite(38, HIGH); // Turn on backlight

  Serial.begin(115200);
  unsigned long startWait = millis();
  while (!Serial && millis() - startWait < 5000);

  // LilyGo Screen Initialization
  tft.init();
  tft.setRotation(1); // Landscape
  tft.fillScreen(TFT_BLACK);
  tft.setTextColor(TFT_WHITE);
  tft.setTextSize(2);
  tft.setCursor(10, 10);
  tft.println("HARDWARE: OK");
  tft.println("CONNECTING WIFI...");

  // Sensor Serial (S3 Hardware Serial 1)
  Serial1.begin(9600, SERIAL_8N1, SENSOR_RX, SENSOR_TX);

  // Initial Wi-Fi Setup
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  int retry = 0;
  while (WiFi.status() != WL_CONNECTED && retry < 20) {
    delay(500);
    tft.print(".");
    retry++;
  }

  configTime(-18000, 3600, ntpServer);

  config.host = FIREBASE_HOST;
  config.signer.tokens.legacy_token = FIREBASE_AUTH;
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  // Load Settings
  if (Firebase.ready()) {
    if (Firebase.getFloat(fbdo, "/settings/tank_empty")) tankEmptyInches = fbdo.floatData();
    if (Firebase.getFloat(fbdo, "/settings/tank_full")) tankFullInches = fbdo.floatData();
  }

  updateDisplay();
}

int getDistanceMM() {
  while (Serial1.available()) Serial1.read();
  delay(100);
  Serial1.write(0x55);
  Serial1.flush();

  unsigned long start = millis();
  while (millis() - start < 600) {
    if (Serial1.available() >= 4) {
      if (Serial1.read() == 0xFF) {
        uint8_t h = Serial1.read();
        uint8_t l = Serial1.read();
        uint8_t sum = Serial1.read();
        if (((0xFF + h + l) & 0xFF) == sum) {
           int mm = (h << 8) | l;
           Serial.printf("Raw Sensor: %d mm\n", mm);
           return mm;
        }
      }
    }
  }
  return -1;
}

float getShellyPower() {
  HTTPClient http;
  http.setTimeout(3000);
  http.begin("http://" + String(shellyIP) + "/rpc/Switch.GetStatus?id=0");
  int httpCode = http.GET();
  float watts = -1.0;
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
  if (WiFi.status() != WL_CONNECTED) {
    WiFi.disconnect();
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    delay(2000);
    return;
  }

  // 1. Pump Check
  currentWatts = getShellyPower();
  bool isPumping = (currentWatts > 10.0);

  if (isPumping && !wasPumping) {
    wasPumping = true;
    pumpStartTime = millis();
    Firebase.pushString(fbdo, "/pump_log", getTimeString());
    Firebase.setString(fbdo, "/status/pump_status", "Pumping...");
  } else if (!isPumping && wasPumping) {
    wasPumping = false;
    unsigned long durationSeconds = (millis() - pumpStartTime) / 1000;
    String durationText = (durationSeconds < 60) ? String(durationSeconds) + " sec" : String(durationSeconds / 60) + " min";
    Firebase.setString(fbdo, "/status/pump_run_duration", durationText);
    Firebase.setString(fbdo, "/status/pump_status", "Idle");
  }

  // 2. Distance Check
  bool forceRefresh = false;
  if (Firebase.ready()) {
    if (Firebase.getBool(fbdo, "/status/refresh_request") && fbdo.boolData()) {
      forceRefresh = true;
      Firebase.setBool(fbdo, "/status/refresh_request", false);
    }
    if (Firebase.getBool(fbdo, "/status/test_mode")) {
      distanceInterval = fbdo.boolData() ? 15000 : 30000;
    }
  }

  if (millis() - lastDistanceUpdate > distanceInterval || lastDistanceUpdate == 0 || forceRefresh) {
    lastDistanceUpdate = millis();
    int distMM = getDistanceMM();

    if (distMM > 0 && Firebase.ready()) {
        lastInches = distMM / 25.4;
        lastPercent = constrain(map(lastInches * 10, tankEmptyInches * 10, tankFullInches * 10, 0, 100), 0, 100);

        // Handle Daily Reset
        struct tm timeinfo;
        if(getLocalTime(&timeinfo)) {
            static int lastDay = -1;
            if (timeinfo.tm_mday != lastDay) { heartbeatCount = 0; lastDay = timeinfo.tm_mday; }
        }
        heartbeatCount++;

        FirebaseJson json;
        json.set("tank_percent", lastPercent);
        json.set("distance_inches", lastInches);
        json.set("heartbeat", heartbeatCount);
        Firebase.updateNode(fbdo, "/status", json);
    }
  }

  // 3. Status Reporting
  if (Firebase.ready()) {
      Firebase.setString(fbdo, "/status/pump_power", (currentWatts >= 0.0) ? "Online" : "Offline");
  }

  updateDisplay();
  delay(5000);
}
