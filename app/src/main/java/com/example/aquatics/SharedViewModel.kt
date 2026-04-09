package com.example.aquatics

import android.app.Application
import android.content.Context
import android.os.CountDownTimer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.aquatics.mqtt.MqttManager
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.*

class SharedViewModel(application: Application) : AndroidViewModel(application) {

    private val _pumpRunning = MutableLiveData<Boolean>(false)
    val pumpRunning: LiveData<Boolean> = _pumpRunning

    private val _irrigationRunning = MutableLiveData<Boolean>(false)
    val irrigationRunning: LiveData<Boolean> = _irrigationRunning

    private val _pumpTimeText = MutableLiveData<String>("00:00")
    val pumpTimeText: LiveData<String> = _pumpTimeText

    private val _irrigationTimeText = MutableLiveData<String>("00:00")
    val irrigationTimeText: LiveData<String> = _irrigationTimeText

    private val _sensorData = MutableLiveData<SensorData?>(null)
    val sensorData: LiveData<SensorData?> = _sensorData

    private var pumpTimer: CountDownTimer? = null
    private var irrigationTimer: CountDownTimer? = null
    private var sensorRequestJob: Job? = null

    private val mqttManager = MqttManager.getInstance(application)

    private val mqttListener: (String, String) -> Unit = { topic, message ->
        val sharedPref = getApplication<Application>().getSharedPreferences("AquaticsSettings", Context.MODE_PRIVATE)
        val sensorTopic = sharedPref.getString("mqtt_sensor_topic", "aquatics/sensors/data")
        if (topic == sensorTopic) {
            try {
                val json = JSONObject(message)
                val dryRunning = json.optBoolean("dry_run", false)
                
                // Logic: If we received a valid message and dry_run is false, status is "OK"
                val dryStatus = if (json.has("dry_run")) {
                    if (dryRunning) "ERROR" else "OK"
                } else {
                    "NA"
                }

                val data = SensorData(
                    level = json.optString("level", "--"),
                    usage = json.optString("usage", "-- Liters"),
                    soil = json.optString("soil", "NA"),
                    flow = json.optString("flow", "NA"),
                    signal = json.optString("signal", "NA"),
                    uptime = json.optString("uptime", "NA"),
                    online = if (json.has("online")) json.getBoolean("online") else null,
                    dryRunString = dryStatus,
                    isDryRunning = dryRunning
                )
                _sensorData.postValue(data)
            } catch (e: Exception) {
                // Parse error
            }
        }
    }

    init {
        mqttManager.addDataListener(mqttListener)
        refreshTimers()
        startConnectionCycle()
    }

    fun startConnectionCycle() {
        val sharedPref = getApplication<Application>().getSharedPreferences("AquaticsSettings", Context.MODE_PRIVATE)
        val broker = sharedPref.getString("mqtt_url", "") ?: ""
        val user = sharedPref.getString("mqtt_user", "") ?: ""
        val pass = sharedPref.getString("mqtt_pass", "") ?: ""

        if (broker.isNotEmpty()) {
            mqttManager.connect(broker, "AquaticsApp_" + UUID.randomUUID().toString().substring(0, 5), user, pass)
            val sensorTopic = sharedPref.getString("mqtt_sensor_topic", "aquatics/sensors/data") ?: "aquatics/sensors/data"
            mqttManager.subscribe(sensorTopic)
            
            if (sensorRequestJob == null || !sensorRequestJob!!.isActive) {
                sensorRequestJob = viewModelScope.launch {
                    while (isActive) {
                        if (mqttManager.isConnected()) {
                            mqttManager.publish(sensorTopic, "GET")
                        }
                        delay(30000)
                    }
                }
            }
        }
    }

    fun refreshTimers() {
        val sharedPref = getApplication<Application>().getSharedPreferences("AquaticsSettings", Context.MODE_PRIVATE)
        if (_pumpRunning.value == false) {
            val pTimer = sharedPref.getInt("pump_timer", 60)
            _pumpTimeText.value = String.format("%02d:00", pTimer)
        }
        if (_irrigationRunning.value == false) {
            val iTimer = sharedPref.getInt("irrigation_timer", 30)
            _irrigationTimeText.value = String.format("%02d:00", iTimer)
        }
    }

    fun togglePump() {
        val sharedPref = getApplication<Application>().getSharedPreferences("AquaticsSettings", Context.MODE_PRIVATE)
        val topic = sharedPref.getString("mqtt_pump_topic", "aquatics/pump/command") ?: "aquatics/pump/command"
        
        if (_pumpRunning.value == true) {
            mqttManager.publish(topic, "OFF")
            _pumpRunning.value = false
            pumpTimer?.cancel()
            refreshTimers()
            AppLogger.info(getApplication(), "PUMP", "Manual Stop")
        } else {
            val duration = sharedPref.getInt("pump_timer", 60)
            mqttManager.publish(topic, "ON")
            _pumpRunning.value = true
            pumpTimer?.cancel()
            pumpTimer = object : CountDownTimer(duration * 60 * 1000L, 1000) {
                override fun onTick(ms: Long) {
                    val sec = ms / 1000
                    _pumpTimeText.postValue(String.format("%02d:%02d", sec / 60, sec % 60))
                }
                override fun onFinish() {
                    mqttManager.publish(topic, "OFF")
                    _pumpRunning.postValue(false)
                    refreshTimers()
                    NotificationHelper.showAlert(getApplication(), "Pump Task Completed", "Cycle finished.")
                }
            }.start()
            AppLogger.info(getApplication(), "PUMP", "Manual Start: $duration min")
        }
    }

    fun toggleIrrigation() {
        val sharedPref = getApplication<Application>().getSharedPreferences("AquaticsSettings", Context.MODE_PRIVATE)
        val topic = sharedPref.getString("mqtt_irrigation_topic", "aquatics/irrigation/command") ?: "aquatics/irrigation/command"

        if (_irrigationRunning.value == true) {
            mqttManager.publish(topic, "OFF")
            _irrigationRunning.value = false
            irrigationTimer?.cancel()
            refreshTimers()
            AppLogger.info(getApplication(), "IRRIGATION", "Manual Stop")
        } else {
            val duration = sharedPref.getInt("irrigation_timer", 30)
            mqttManager.publish(topic, "ON")
            _irrigationRunning.value = true
            irrigationTimer?.cancel()
            irrigationTimer = object : CountDownTimer(duration * 60 * 1000L, 1000) {
                override fun onTick(ms: Long) {
                    val sec = ms / 1000
                    _irrigationTimeText.postValue(String.format("%02d:%02d", sec / 60, sec % 60))
                }
                override fun onFinish() {
                    mqttManager.publish(topic, "OFF")
                    _irrigationRunning.postValue(false)
                    refreshTimers()
                    NotificationHelper.showAlert(getApplication(), "Irrigation Completed", "Watering finished.")
                }
            }.start()
            AppLogger.info(getApplication(), "IRRIGATION", "Manual Start: $duration min")
        }
    }

    override fun onCleared() {
        super.onCleared()
        mqttManager.removeDataListener(mqttListener)
        pumpTimer?.cancel()
        irrigationTimer?.cancel()
        sensorRequestJob?.cancel()
    }
}

data class SensorData(
    val level: String,
    val usage: String,
    val soil: String,
    val flow: String,
    val signal: String,
    val uptime: String,
    val online: Boolean?,
    val dryRunString: String,
    val isDryRunning: Boolean
)
