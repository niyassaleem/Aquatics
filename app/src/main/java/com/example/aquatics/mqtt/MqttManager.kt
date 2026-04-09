package com.example.aquatics.mqtt

import android.content.Context
import com.example.aquatics.AppLogger
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import javax.net.ssl.SSLSocketFactory

class MqttManager private constructor(private val context: Context) {

    private var mqttClient: MqttAsyncClient? = null
    private var statusListener: ((Boolean) -> Unit)? = null
    private val dataListeners = mutableListOf<(String, String) -> Unit>()
    
    // Cache for the last received data per topic
    private val lastDataCache = mutableMapOf<String, String>()
    
    private var currentUrl: String? = null
    private var currentUser: String? = null

    companion object {
        @Volatile
        private var INSTANCE: MqttManager? = null

        fun getInstance(context: Context): MqttManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MqttManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun setOnConnectionStatusChangedListener(listener: (Boolean) -> Unit) {
        this.statusListener = listener
        listener(isConnected())
    }

    fun addDataListener(listener: (String, String) -> Unit) {
        if (!dataListeners.contains(listener)) {
            dataListeners.add(listener)
        }
        // Immediately push cached data to new listener
        lastDataCache.forEach { (topic, data) ->
            listener(topic, data)
        }
    }

    fun removeDataListener(listener: (String, String) -> Unit) {
        dataListeners.remove(listener)
    }

    fun getLastData(topic: String): String? {
        return lastDataCache[topic]
    }

    fun connect(brokerUrl: String, clientId: String, userName: String? = null, password: String? = null) {
        val rawUrl = brokerUrl.trim()
            .replace("ssl://", "")
            .replace("tcp://", "")
            .replace("https://", "")
            .replace("http://", "")
        
        var cleanDomain = rawUrl
        while (cleanDomain.startsWith(".") || cleanDomain.startsWith("/")) {
            cleanDomain = cleanDomain.substring(1)
        }

        val finalUrl = "ssl://$cleanDomain"
        val urlWithPort = if (!finalUrl.contains(":")) "$finalUrl:8883" else finalUrl

        if (mqttClient?.isConnected == true && urlWithPort == currentUrl && userName == currentUser) {
            statusListener?.invoke(true)
            return
        }

        try {
            if (mqttClient?.isConnected == true) {
                mqttClient?.disconnect()
            }

            currentUrl = urlWithPort
            currentUser = userName

            mqttClient = MqttAsyncClient(urlWithPort, clientId, MemoryPersistence())

            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
                connectionTimeout = 30
                keepAliveInterval = 60
                mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1
                socketFactory = SSLSocketFactory.getDefault()

                if (!userName.isNullOrBlank()) this.userName = userName
                if (!password.isNullOrBlank()) this.password = password.toCharArray()
            }

            mqttClient?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    AppLogger.info(context, "MQTT", if (reconnect) "Reconnected" else "Connected to Cluster")
                    statusListener?.invoke(true)
                }

                override fun connectionLost(cause: Throwable?) {
                    AppLogger.warn(context, "MQTT", "Connection lost: ${cause?.message}")
                    statusListener?.invoke(false)
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val payload = message?.toString() ?: ""
                    val safeTopic = topic ?: ""
                    
                    // Update Cache
                    lastDataCache[safeTopic] = payload
                    
                    AppLogger.info(context, "MQTT_IN", "[$safeTopic] $payload")
                    dataListeners.forEach { it.invoke(safeTopic, payload) }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {}
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    val message = if (exception is MqttException) {
                        "Reason ${exception.reasonCode}: ${exception.message}"
                    } else {
                        exception?.message ?: "Handshake Failed"
                    }
                    AppLogger.error(context, "MQTT", "Failed: $message")
                    statusListener?.invoke(false)
                }
            })

        } catch (e: Exception) {
            AppLogger.error(context, "MQTT", "Fatal Error: ${e.message}")
            statusListener?.invoke(false)
        }
    }

    fun isConnected(): Boolean = mqttClient?.isConnected ?: false

    fun publish(topic: String, payload: String) {
        if (!isConnected()) {
            AppLogger.warn(context, "MQTT", "Publish failed: Not connected")
            return
        }
        try {
            mqttClient?.publish(topic, payload.toByteArray(), 1, false)
        } catch (e: Exception) {
            AppLogger.error(context, "MQTT", "Publish Error: ${e.message}")
        }
    }

    fun subscribe(topic: String) {
        if (!isConnected()) return
        try {
            mqttClient?.subscribe(topic, 1)
            AppLogger.info(context, "MQTT", "Subscribing to $topic")
        } catch (e: Exception) {
            AppLogger.error(context, "MQTT", "Subscribe Error: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
            mqttClient = null
        } catch (e: Exception) {}
    }
}
