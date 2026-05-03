#include <ESP8266WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>

// ==========================================
// FEATURE TOGGLES
// ==========================================
#define USE_SOIL_SENSOR
#define USE_LEVEL_SENSOR         // Digital Level Sensor (Float Switch)
#define USE_PUMP_CONTROL
#define USE_IRRIGATION_CONTROL
#define USE_DRY_RUN_PROTECTION   

// ==========================================
// CONFIGURATION & SAFETY TIMERS
// ==========================================
const char* ssid     = "YOUR_WIFI_SSID";
const char* password = "YOUR_WIFI_PASSWORD";

// HiveMQ Cloud Broker
const char* mqtt_server = "e5cd970367924076ba08be31e69e4301.s1.eu.hivemq.cloud";
const int   mqtt_port   = 8883; 
const char* mqtt_user   = "Niyas24";
const char* mqtt_pass   = "YOUR_MQTT_PASSWORD";

// Hardware Failsafes (Safety Limits)
const unsigned long MAX_PUMP_RUN_TIME = 65 * 60 * 1000; // 65 mins max
const unsigned long MAX_IRR_RUN_TIME  = 35 * 60 * 1000; // 35 mins max
const unsigned long CONN_LOSS_TIMEOUT = 5 * 60 * 1000;  // 5 mins disconnect = shutdown

// MQTT Topics
const char* TOPIC_PUMP       = "aquatics/pump/command";
const char* TOPIC_IRRIGATION = "aquatics/irrigation/command";
const char* TOPIC_SENSORS    = "aquatics/sensors/data";

// ==========================================
// PIN DEFINITIONS
// ==========================================
#define PIN_SOIL       A0        
#define PIN_LEVEL      D3        // D5
#define PIN_PUMP       D1        // D6
#define PIN_IRRIGATION D2        // D7

// Global State Tracking
WiFiClientSecure espClient;
PubSubClient client(espClient);
unsigned long lastMsg = 0;
unsigned long disconnectTime = 0;

// Task Timing for Failsafes
unsigned long pumpStartTime = 0;
unsigned long irrStartTime = 0;
bool pumpActive = false;
bool irrActive = false;

void setup() {
  Serial.begin(115200);
  
  pinMode(PIN_PUMP, OUTPUT);
  digitalWrite(PIN_PUMP, LOW);
  pinMode(PIN_IRRIGATION, OUTPUT);
  digitalWrite(PIN_IRRIGATION, LOW);
  
  #ifdef USE_LEVEL_SENSOR
    pinMode(PIN_LEVEL, INPUT_PULLUP);
  #endif

  setup_wifi();
  espClient.setInsecure(); // Required for HiveMQ SSL
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
  Serial.println("\nWiFi Connected");
}

void callback(char* topic, byte* payload, unsigned int length) {
  String message;
  for (int i = 0; i < length; i++) message += (char)payload[i];
  
  Serial.println("Command [" + String(topic) + "]: " + message);

  if (String(topic) == TOPIC_SENSORS && message == "GET") {
    sendSensorData();
    return;
  }

  if (String(topic) == TOPIC_PUMP) {
    if (message == "ON") {
      digitalWrite(PIN_PUMP, HIGH);
      pumpActive = true;
      pumpStartTime = millis();
    } else {
      digitalWrite(PIN_PUMP, LOW);
      pumpActive = false;
    }
    sendSensorData(); 
  }

  if (String(topic) == TOPIC_IRRIGATION) {
    if (message == "ON") {
      digitalWrite(PIN_IRRIGATION, HIGH);
      irrActive = true;
      irrStartTime = millis();
    } else {
      digitalWrite(PIN_IRRIGATION, LOW);
      irrActive = false;
    }
    sendSensorData();
  }
}

void sendSensorData() {
  StaticJsonDocument<512> doc;
  
  doc["online"] = true; 
  doc["signal"] = String(WiFi.RSSI()) + " dBm";
  
  long totalSec = millis() / 1000;
  doc["uptime"] = String(totalSec / 3600) + "h " + String((totalSec % 3600) / 60) + "m";

  #ifdef USE_LEVEL_SENSOR
    bool tankLow = (digitalRead(PIN_LEVEL) == HIGH);
    doc["level"] = tankLow ? "LOW" : "OK";
  #else
    doc["level"] = "NA";
  #endif

  doc["usage"] = "NA";
  doc["flow"]  = "NA";

  #ifdef USE_SOIL_SENSOR
    int soilPer = map(analogRead(PIN_SOIL), 1024, 200, 0, 100);
    doc["soil"] = String(constrain(soilPer, 0, 100)) + "%";
  #else
    doc["soil"] = "NA";
  #endif

  #ifdef USE_DRY_RUN_PROTECTION
    bool dryError = (pumpActive && (digitalRead(PIN_LEVEL) == HIGH));
    doc["dry_run"] = dryError;
  #else
    doc["dry_run"] = false;
  #endif

  char buffer[512];
  serializeJson(doc, buffer);
  client.publish(TOPIC_SENSORS, buffer);
}

void reconnect() {
  while (!client.connected()) {
    String clientId = "ESP12-Aquatics-" + String(random(0xffff), HEX);
    if (client.connect(clientId.c_str(), mqtt_user, mqtt_pass)) {
      client.subscribe(TOPIC_PUMP);
      client.subscribe(TOPIC_IRRIGATION);
      client.subscribe(TOPIC_SENSORS);
      disconnectTime = 0; // Reset disconnect watchdog
    } else {
      delay(5000);
    }
  }
}

void loop() {
  if (!client.connected()) {
    if (disconnectTime == 0) disconnectTime = millis();
    reconnect();
  } else {
    disconnectTime = 0;
  }
  client.loop();

  unsigned long now = millis();

  // --- FAILSAFE 1: Local Max Run-Time Protection ---
  if (pumpActive && (now - pumpStartTime > MAX_PUMP_RUN_TIME)) {
    digitalWrite(PIN_PUMP, LOW);
    pumpActive = false;
    Serial.println("SAFETY: Local Pump Timeout!");
    sendSensorData();
  }
  if (irrActive && (now - irrStartTime > MAX_IRR_RUN_TIME)) {
    digitalWrite(PIN_IRRIGATION, LOW);
    irrActive = false;
    Serial.println("SAFETY: Local Irrigation Timeout!");
    sendSensorData();
  }

  // --- FAILSAFE 2: Internet Connection Loss Watchdog ---
  if (disconnectTime != 0 && (now - disconnectTime > CONN_LOSS_TIMEOUT)) {
    if (pumpActive || irrActive) {
      digitalWrite(PIN_PUMP, LOW);
      digitalWrite(PIN_IRRIGATION, LOW);
      pumpActive = false;
      irrActive = false;
      Serial.println("SAFETY: Emergency Shutdown (No Internet)");
    }
  }

  // Auto-send data every 30 seconds
  if (now - lastMsg > 30000) {
    lastMsg = now;
    sendSensorData();
  }
}
