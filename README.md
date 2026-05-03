# Aquatics - Smart Water Management System

Aquatics is a modern Android application designed for real-time monitoring and control of water systems. It integrates with ESP8266 (ESP12) hardware via MQTT to provide a seamless automated irrigation and pump management experience.

## 📱 App Features

- **Real-time Dashboard**: Monitor water levels, soil moisture, and system status instantly.
- **Remote Control**: Manually toggle Water Pumps and Irrigation systems from anywhere in the world.
- **Weather Integration**: Displays local weather data (Temperature, Condition, Sunrise/Sunset) using Retrofit and OpenWeatherMap API to help you decide when to water.
- **Smart Safety (Dry Run Protection)**: Automatically detects and alerts you if the pump is running without water, preventing hardware damage.
- **Local Logging**: Uses **Room Database** to store a history of system events, errors, and manual overrides for offline viewing.
- **Advanced Settings**: Fully customizable MQTT broker details, topic configurations, and sensor sensitivity toggles.

## 🛠 Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM (Model-View-ViewModel)
- **Networking**: Retrofit 2 (Weather API), Paho MQTT (Hardware Communication)
- **Database**: Room Persistence Library (Event Logging)
- **UI**: Material Design 3, Fragment-ktx, ConstraintLayout
- **Concurrency**: Kotlin Coroutines & Flow

## 🔌 ESP12 (Hardware) Side

The system is designed to work with an ESP12/NodeMCU module.

### Hardware Requirements
- ESP8266 (ESP12) Module
- Relay Module (for Pump & Irrigation)
- Digital Water Level Sensor (Float Switch)
- Analog Soil Moisture Sensor
- 5V/12V Power Supply

### Pinout Mapping
| Component | ESP12 Pin | Description |
|-----------|-----------|-------------|
| **Soil Sensor** | A0 | Analog moisture reading |
| **Level Sensor**| D5 (GPIO 14)| Digital input (LOW = Water OK) |
| **Pump Relay**  | D6 (GPIO 12)| Digital output to control pump |
| **Irrigation**  | D7 (GPIO 13)| Digital output to control valves|

### ESP Setup Instructions
1. Open the provided Arduino sketch in the Arduino IDE.
2. Install **ArduinoJson** and **PubSubClient** libraries.
3. Update the `ssid`, `password`, and `mqtt_server` (HiveMQ Cloud URL) in the sketch.
4. Flash the code to your ESP12 module.

## 📖 User Manual

### 1. Connection Setup
- Open the app and navigate to **Settings**.
- Enter your **MQTT Broker URL**, **Username**, and **Password**.
- Ensure the **Sensor Topic** matches the one defined in your ESP code (default: `aquatics/sensors/data`).

### 2. Weather Configuration
- Obtain an API key from OpenWeatherMap.
- Enter the key and your city name in the app settings to enable weather-aware dashboarding.

### 3. Monitoring & Control
- On the **Home** tab, use the circular cards to toggle the Pump or Irrigation.
- The **Stats** tab provides a detailed breakdown of signal strength, system uptime, and the "Dry Run" safety status.
- View the **Logs** tab to see a timestamped history of every action taken by the system.

## ⚙️ Development
To build the project:
1. Clone the repository.
2. Open in Android Studio (Iguana or newer recommended).
3. Sync Gradle to download dependencies (Room, Retrofit, Paho MQTT).
4. Build and run on an Android device (API 24+).
