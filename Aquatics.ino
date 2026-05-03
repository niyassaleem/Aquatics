// Safety: Maximum time a pump can run in one go (e.g., 65 minutes)
// This should be slightly longer than your max app timer.
const unsigned long MAX_PUMP_RUN_TIME = 65 * 60 * 1000; 
const unsigned long MAX_IRR_RUN_TIME  = 35 * 60 * 1000;

unsigned long pumpStartTime = 0;
unsigned long irrStartTime = 0;
bool pumpActive = false;
bool irrActive = false;

void callback(char* topic, byte* payload, unsigned int length) {
  // ... (previous message parsing) ...

  if (String(topic) == TOPIC_PUMP) {
    if (message == "ON") {
      digitalWrite(PIN_PUMP, HIGH);
      pumpStartTime = millis();
      pumpActive = true;
    } else {
      digitalWrite(PIN_PUMP, LOW);
      pumpActive = false;
    }
  }
  
  if (String(topic) == TOPIC_IRRIGATION) {
    if (message == "ON") {
      digitalWrite(PIN_IRRIGATION, HIGH);
      irrStartTime = millis();
      irrActive = true;
    } else {
      digitalWrite(PIN_IRRIGATION, LOW);
      irrActive = false;
    }
  }
}

void loop() {
  // ... (previous MQTT connection logic) ...

  unsigned long now = millis();

  // FAILSAFE 1: Local Timer Timeout
  if (pumpActive && (now - pumpStartTime > MAX_PUMP_RUN_TIME)) {
    digitalWrite(PIN_PUMP, LOW);
    pumpActive = false;
    Serial.println("SAFETY: Pump stopped by local failsafe timer!");
  }

  if (irrActive && (now - irrStartTime > MAX_IRR_RUN_TIME)) {
    digitalWrite(PIN_IRRIGATION, LOW);
    irrActive = false;
    Serial.println("SAFETY: Irrigation stopped by local failsafe timer!");
  }

  // FAILSAFE 2: Connection Loss
  // If internet breaks for more than 5 minutes while pump is on, turn it off.
  if (!client.connected() && (pumpActive || irrActive)) {
    static unsigned long disconnectTime = 0;
    if (disconnectTime == 0) disconnectTime = now;
    
    if (now - disconnectTime > 300000) { // 5 minutes
       digitalWrite(PIN_PUMP, LOW);
       digitalWrite(PIN_IRRIGATION, LOW);
       pumpActive = false;
       irrActive = false;
       Serial.println("SAFETY: Connection lost. Emergency shutdown.");
    }
  } else {
    disconnectTime = 0;
  }
}
