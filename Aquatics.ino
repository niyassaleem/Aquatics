#include <ESP8266WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>

// ==========================================
// FEATURE TOGGLES - Comment out to disable
// ==========================================
#define USE_SOIL_SENSOR
#define USE_LEVEL_SENSOR         // Digital Water Level Sensor
#define USE_FLOW_SENSOR
#define USE_PUMP_CONTROL
#define USE_IRRIGATION_CONTROL
#define USE_DRY_RUN_PROTECTION   // Requires LEVEL_SENSOR or FLOW_SENSOR

// ==========================================
// CONFIGURATION
// ==========================================
const char* ssid     = "SALEEM";
const char* password = "bsnl2992573";

// HiveMQ Cloud Broker (from app settings)
const char* mqtt_server = "e5cd970367924076ba08be31e69e4301.s1.eu.hivemq.cloud";
const int   mqtt_port   = 8883; // SSL Port
const char* mqtt_user   = "Niyas24";
const char* mqtt_pass   = "AjK8i-;Un8Nh\"Gp";

// MQTT Topics (Matching App Defaults)
const char* TOPIC_PUMP       = "aquatics/pump/command";
const char* TOPIC_IRRIGATION = "aquatics/irrigation/command";
const char* TOPIC_SENSORS    = "aquatics/sensors/data";

// ==========================================
// PIN DEFINITIONS (ESP12 / NodeMCU)
// ==========================================
#define PIN_SOIL       A0        // Soil Moisture (Analog)
#define PIN_LEVEL      14        // Level Sensor (D5 - Digital)
#define PIN_PUMP       12        // Pump Relay (D6)
#define PIN_IRRIGATION 13        // Irrigation Relay (D7)
#define PIN_FLOW       4         // Flow Sensor (D2)

// Global Objects
WiFiClientSecure espClient;
PubSubClient client(espClient);
unsigned long lastMsg = 0;
volatile long pulseCount = 0;

// Interrupt for Flow Sensor
void IRAM_ATTR pulseCounter() {
  pulseCount++;
}

void setup() {
  Serial.begin(115200);
  
  // Initialize Pins
  #ifdef USE_PUMP_CONTROL
    pinMode(PIN_PUMP, OUTPUT);
    digitalWrite(PIN_PUMP, LOW);
  #endif

  #ifdef USE_IRRIGATION_CONTROL
    pinMode(PIN_IRRIGATION, OUTPUT);
    digitalWrite(PIN_IRRIGATION, LOW);
  #endif

  #ifdef USE_LEVEL_SENSOR
    pinMode(PIN_LEVEL, INPUT_PULLUP);
  #endif

  #ifdef USE_FLOW_SENSOR
    pinMode(PIN_FLOW, INPUT_PULLUP);
    attachInterrupt(digitalPinToInterrupt(PIN_FLOW), pulseCounter, FALLING);
  #endif

  setup_wifi();

  // SSL Setup for HiveMQ Cloud
  espClient.setInsecure(); // Skips certificate validation
  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(callback);
}

void setup_wifi() {
  delay(10);
  Serial.println("\nConnecting to WiFi...");
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected. IP: " + WiFi.localIP().toString());
}

// Handle Incoming Commands from App
void callback(char* topic, byte* payload, unsigned int length) {
  String message;
  for (int i = 0; i < length; i++) message += (char)payload[i];
  
  Serial.println("Message [" + String(topic) + "]: " + message);

  // App requested immediate update
  if (String(topic) == TOPIC_SENSORS && message == "GET") {
    sendSensorData();
    return;
  }

  #ifdef USE_PUMP_CONTROL
  if (String(topic) == TOPIC_PUMP) {
    digitalWrite(PIN_PUMP, (message == "ON") ? HIGH : LOW);
    sendSensorData(); // Report status back
  }
  #endif

  #ifdef USE_IRRIGATION_CONTROL
  if (String(topic) == TOPIC_IRRIGATION) {
    digitalWrite(PIN_IRRIGATION, (message == "ON") ? HIGH : LOW);
    sendSensorData();
  }
  #endif
}

// 100% Compatible JSON Payload for Android App
void sendSensorData() {
  StaticJsonDocument<512> doc;
  
  // 1. Hardware Heartbeat & Signal
  doc["online"] = true; 
  doc["signal"] = String(WiFi.RSSI()) + " dBm";
  
  // 2. System Uptime (Required by StatsFragment)
  long totalSeconds = millis() / 1000;
  int hours = totalSeconds / 3600;
  int mins = (totalSeconds % 3600) / 60;
  doc["uptime"] = String(hours) + "h " + String(mins) + "m";

  // 3. Water Level Logic
  #ifdef USE_LEVEL_SENSOR
    // Assuming LOW means water is touching the sensor (Tank OK)
    bool tankLow = (digitalRead(PIN_LEVEL) == HIGH);
    //doc["level"] = tankLow ? "LOW" : "FULL";
    //doc["usage"] = String(random(5, 15)) + " Liters"; // Example mock data
  #else
    doc["level"] = "NA";
  #endif

  // 4. Soil Moisture Logic
  #ifdef USE_SOIL_SENSOR
    int soilVal = analogRead(PIN_SOIL);
    int soilPer = map(soilVal, 1024, 200, 0, 100);
    doc["soil"] = String(constrain(soilPer, 0, 100)) + "%";
  #else
    doc["soil"] = "NA";
  #endif

  // 5. Flow Rate Logic
  #ifdef USE_FLOW_SENSOR
    float litersPerMin = (pulseCount / 7.5); // standard YF-S201 formula
    doc["flow"] = String(litersPerMin, 1) + " L/m";
    pulseCount = 0; // reset for next interval
  #else
    doc["flow"] = "NA";
  #endif

  // 6. Dry Run Protection Logic (SharedViewModel parsing)
  #ifdef USE_DRY_RUN_PROTECTION
    bool pumpOn = (digitalRead(PIN_PUMP) == HIGH);
    // If pump is active but level is LOW (empty), trigger Dry Run Error
    bool dryError = (pumpOn && (digitalRead(PIN_LEVEL) == HIGH));
    doc["dry_run"] = dryError;
  #else
    doc["dry_run"] = false;
  #endif

  char buffer[512];
  serializeJson(doc, buffer);
  client.publish(TOPIC_SENSORS, buffer);
  Serial.println("Data Published to App");
}

void reconnect() {
  while (!client.connected()) {
    Serial.print("Attempting MQTT connection...");
    String clientId = "ESP12-Aquatics-" + String(random(0xffff), HEX);
    
    if (client.connect(clientId.c_str(), mqtt_user, mqtt_pass)) {
      Serial.println("connected");
      client.subscribe(TOPIC_PUMP);
      client.subscribe(TOPIC_IRRIGATION);
      client.subscribe(TOPIC_SENSORS);
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 5 seconds");
      delay(5000);
    }
  }
}

void loop() {
  if (!client.connected()) reconnect();
  client.loop();

  // Automatic Data Update (Every 30 seconds as per App design)
  unsigned long now = millis();
  if (now - lastMsg > 30000) {
    lastMsg = now;
    sendSensorData();
  }
}
