# SepticMonitor IoT System

A dual-purpose, solar-powered IoT system designed to monitor septic tank liquid levels and track pump activity in real-time. The system uses a LilyGo T-Display-S3 (ESP32-S3) at the tank and integrates with a Shelly Plus smart plug for pump monitoring, centralized through Firebase and a custom Android application.

## 🚀 Key Features

### Hardware & Firmware (ESP32-S3)
- **Real-Time Level Sensing:** Uses the Dioche A02YYUW Waterproof Ultrasonic Sensor for high-precision depth monitoring.
- **Pump Analytics:** Monitors pump wattage via Shelly Plus API; logs every pump start and calculates exact run durations (e.g., "2 min 15 sec").
- **Smart Power Management:** Built-in 1.9" LCD with 10-minute auto-timeout and physical wake-button support to conserve solar battery life.
- **Remote Calibration:** Fetches tank dimensions (Empty/Full inches) directly from Firebase, allowing for field calibration without re-flashing.
- **Stability:** Features a Wi-Fi Watchdog and NTP-synced daily heartbeat reset to monitor system uptime.

### Android Application
- **Live Dashboard:** Real-time progress bar for tank levels and dynamic color-coded system status (Green/Yellow/Red).
- **Emergency Control:** Integrated bridge to remotely toggle the Shelly smart plug power from anywhere in the world.
- **Daily Reporting:** Automated 12:00 PM system report sent via email to mackeyrj@gmail.com, including daily level trends and pump activity.
- **History Log:** View the last five unique level changes to spot trends in tank filling or usage.

## 🛠 Hardware Architecture
- **Controller:** LilyGo T-Display-S3 (ESP32-S3).
- **Sensor:** Dioche A02YYUW Waterproof Ultrasonic Sensor (UART).
- **Pump Monitor:** Shelly Plus 1PM (or similar) Smart Plug.
- **Power System:** Solar Panel -> Charge Controller -> 12V Battery -> 12V-to-5V USB-C Step-down converter.
- **Noise Filtering:** 1000uF Electrolytic and 0.1uF Ceramic capacitors on the 12V input block to filter solar charging ripple.

## 📂 Project Structure
- `/app`: Android Studio project (Kotlin, Jetpack Compose, Material3).
- `/esp32/SepticMonitor`: Primary firmware for the LilyGo S3.
- `/esp32/SensorTest`: Hardware diagnostic tools for sensor validation.
- `/esp32/ShellyTest`: API testing tools for Shelly Plus integration.

## ⚙️ Setup & Configuration

### Firebase
1. Create a Firebase Realtime Database.
2. Set Security Rules to `.read: true` and `.write: true` (or use Authentication).
3. Structure:
   - `/status`: Current levels, heartbeat, and pump state.
   - `/settings`: `tank_empty` and `tank_full` calibration values.
   - `/pump_log`: Historical archive of pump activity.

### Arduino
1. Install `TFT_eSPI`, `FirebaseESP32`, and `ArduinoJson` libraries.
2. In `TFT_eSPI/User_Setup_Select.h`, ensure `Setup206_LilyGo_T_Display_S3.h` is the ONLY active setup.
3. Configure `secrets.h` with your Wi-Fi and Firebase credentials.
4. Set **USB CDC On Boot** to **Enabled** in the IDE Tools menu.

### Android
1. Add your `google-services.json` to the `/app` folder.
2. Update the Firebase URL in `MainActivity.kt`.
3. Build and deploy to your Android device.

## 📜 Documentation
Full project history and technical details are maintained in `ProjectSummary.txt`.
