#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include "secrets.h"

// The IP address of your Shelly Plus
const char* shellyIP = "192.168.86.43";

void setup() {
  Serial.begin(115200);

  // Connect to Wi-Fi using your existing secrets
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting to Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nConnected!");
  Serial.println("Starting Shelly Power Test...");
}

void loop() {
  if (WiFi.status() == WL_CONNECTED) {
    HTTPClient http;

    // Construct the RPC URL for Shelly Gen2/Gen4 devices
    String url = "http://" + String(shellyIP) + "/rpc/Switch.GetStatus?id=0";

    http.begin(url);
    int httpCode = http.GET();

    if (httpCode > 0) {
      String payload = http.getString();

      // Parse the JSON response
      StaticJsonDocument<512> doc;
      DeserializationError error = deserializeJson(doc, payload);

      if (!error) {
        float power = doc["apower"]; // Active power in Watts
        float voltage = doc["voltage"];
        bool isOn = doc["output"];

        Serial.print("Lamp is: ");
        Serial.print(isOn ? "ON" : "OFF");
        Serial.print(" | Power: ");
        Serial.print(power);
        Serial.print("W | Voltage: ");
        Serial.print(voltage);
        Serial.println("V");
      } else {
        Serial.print("JSON Parse Failed: ");
        Serial.println(error.c_str());
      }
    } else {
      Serial.print("HTTP Request Failed: ");
      Serial.println(http.errorToString(httpCode).c_str());
    }

    http.end();
  }

  // Check every 3 seconds
  delay(3000);
}
