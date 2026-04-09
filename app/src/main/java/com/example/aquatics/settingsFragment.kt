package com.example.aquatics

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONArray

class SettingsFragment : Fragment() {

    private lateinit var editWeatherApiKey: TextInputEditText
    private lateinit var editWeatherCity: TextInputEditText
    
    private lateinit var editMqttUrl: TextInputEditText
    private lateinit var editMqttUsername: TextInputEditText
    private lateinit var editMqttPassword: TextInputEditText
    private lateinit var editMqttPumpTopic: TextInputEditText
    private lateinit var editMqttIrrigationTopic: TextInputEditText
    private lateinit var editMqttSensorTopic: TextInputEditText
    
    private lateinit var mqttContainer: LinearLayout
    private lateinit var btnAddTopic: MaterialButton
    private val dynamicTopicFields = mutableListOf<TextInputEditText>()
    
    private lateinit var editPumpTimer: TextInputEditText
    private lateinit var editIrrigationTimer: TextInputEditText

    private lateinit var switchTaskNotify: MaterialSwitch
    private lateinit var switchErrorNotify: MaterialSwitch
    
    private lateinit var switchSoilSensor: MaterialSwitch
    private lateinit var switchLevelSensor: MaterialSwitch
    private lateinit var switchFlowSensor: MaterialSwitch
    
    private lateinit var btnSave: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        // Initialize views
        editWeatherApiKey = view.findViewById(R.id.editWeatherApiKey)
        editWeatherCity = view.findViewById(R.id.editWeatherCity)
        
        editMqttUrl = view.findViewById(R.id.editMqttUrl)
        editMqttUsername = view.findViewById(R.id.editMqttUsername)
        editMqttPassword = view.findViewById(R.id.editMqttPassword)
        editMqttPumpTopic = view.findViewById(R.id.editMqttPumpTopic)
        editMqttIrrigationTopic = view.findViewById(R.id.editMqttIrrigationTopic)
        editMqttSensorTopic = view.findViewById(R.id.editMqttSensorTopic)
        
        mqttContainer = view.findViewById(R.id.mqttContainer)
        btnAddTopic = view.findViewById(R.id.btnAddTopic)
        
        editPumpTimer = view.findViewById(R.id.editPumpTimer)
        editIrrigationTimer = view.findViewById(R.id.editIrrigationTimer)

        switchTaskNotify = view.findViewById(R.id.switchTaskNotifications)
        switchErrorNotify = view.findViewById(R.id.switchErrorNotifications)
        
        switchSoilSensor = view.findViewById(R.id.switchSoilSensor)
        switchLevelSensor = view.findViewById(R.id.switchLevelSensor)
        switchFlowSensor = view.findViewById(R.id.switchFlowSensor)
        
        btnSave = view.findViewById(R.id.btnSaveSettings)

        loadSettings()

        btnAddTopic.setOnClickListener {
            addDynamicTopicField("")
        }

        btnSave.setOnClickListener {
            saveSettings()
        }

        return view
    }

    private fun addDynamicTopicField(value: String) {
        val context = requireContext()
        val textInputLayout = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            hint = "Custom Topic"
            endIconMode = TextInputLayout.END_ICON_CUSTOM
            setEndIconDrawable(android.R.drawable.ic_delete)
            setEndIconContentDescription("Remove topic")
        }
        
        // Use 12dp margin
        val margin12 = (12 * resources.displayMetrics.density).toInt()
        (textInputLayout.layoutParams as LinearLayout.LayoutParams).topMargin = margin12

        val editText = TextInputEditText(textInputLayout.context)
        editText.setText(value)
        textInputLayout.addView(editText)
        
        textInputLayout.setEndIconOnClickListener {
            mqttContainer.removeView(textInputLayout)
            dynamicTopicFields.remove(editText)
        }
        
        // Add before the "Add More" button
        val index = mqttContainer.indexOfChild(btnAddTopic)
        mqttContainer.addView(textInputLayout, index)
        dynamicTopicFields.add(editText)
    }

    private fun loadSettings() {
        val sharedPref = requireActivity().getSharedPreferences("AquaticsSettings", Context.MODE_PRIVATE)
        
        editWeatherApiKey.setText(sharedPref.getString("weather_api_key", ""))
        editWeatherCity.setText(sharedPref.getString("weather_city", "Kochi"))
        
        editMqttUrl.setText(sharedPref.getString("mqtt_url", "ssl://e5cd970367924076ba08be31e69e4301.s1.eu.hivemq.cloud:8883"))
        editMqttUsername.setText(sharedPref.getString("mqtt_user", "Niyas24"))
        editMqttPassword.setText(sharedPref.getString("mqtt_pass", ""))
        editMqttPumpTopic.setText(sharedPref.getString("mqtt_pump_topic", "aquatics/pump/command"))
        editMqttIrrigationTopic.setText(sharedPref.getString("mqtt_irrigation_topic", "aquatics/irrigation/command"))
        editMqttSensorTopic.setText(sharedPref.getString("mqtt_sensor_topic", "aquatics/sensors/data"))
        
        // Load dynamic topics
        val dynamicTopicsJson = sharedPref.getString("mqtt_dynamic_topics", "[]")
        try {
            val jsonArray = JSONArray(dynamicTopicsJson)
            for (i in 0 until jsonArray.length()) {
                addDynamicTopicField(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        editPumpTimer.setText(sharedPref.getInt("pump_timer", 60).toString())
        editIrrigationTimer.setText(sharedPref.getInt("irrigation_timer", 30).toString())

        switchTaskNotify.isChecked = sharedPref.getBoolean("notify_task", true)
        switchErrorNotify.isChecked = sharedPref.getBoolean("notify_error", true)
        
        switchSoilSensor.isChecked = sharedPref.getBoolean("sensor_soil", true)
        switchLevelSensor.isChecked = sharedPref.getBoolean("sensor_level", true)
        switchFlowSensor.isChecked = sharedPref.getBoolean("sensor_flow", false)
    }

    private fun saveSettings() {
        val sharedPref = requireActivity().getSharedPreferences("AquaticsSettings", Context.MODE_PRIVATE)
        
        val pumpVal = editPumpTimer.text.toString().trim().toIntOrNull() ?: 60
        val irrVal = editIrrigationTimer.text.toString().trim().toIntOrNull() ?: 30

        sharedPref.edit(commit = true) {
            putString("weather_api_key", editWeatherApiKey.text.toString().trim())
            putString("weather_city", editWeatherCity.text.toString().trim())
            
            putString("mqtt_url", editMqttUrl.text.toString().trim())
            putString("mqtt_user", editMqttUsername.text.toString().trim())
            putString("mqtt_pass", editMqttPassword.text.toString().trim())
            putString("mqtt_pump_topic", editMqttPumpTopic.text.toString().trim())
            putString("mqtt_irrigation_topic", editMqttIrrigationTopic.text.toString().trim())
            putString("mqtt_sensor_topic", editMqttSensorTopic.text.toString().trim())
            
            // Save dynamic topics
            val jsonArray = JSONArray()
            for (field in dynamicTopicFields) {
                val topic = field.text.toString().trim()
                if (topic.isNotEmpty()) {
                    jsonArray.put(topic)
                }
            }
            putString("mqtt_dynamic_topics", jsonArray.toString())
            
            putInt("pump_timer", pumpVal)
            putInt("irrigation_timer", irrVal)

            putBoolean("notify_task", switchTaskNotify.isChecked)
            putBoolean("notify_error", switchErrorNotify.isChecked)
            
            putBoolean("sensor_soil", switchSoilSensor.isChecked)
            putBoolean("sensor_level", switchLevelSensor.isChecked)
            putBoolean("sensor_flow", switchFlowSensor.isChecked)
        }
        
        AppLogger.info(requireContext(), "SETTINGS", "Configuration Saved. Timers: Pump=$pumpVal, Irr=$irrVal")
        Toast.makeText(requireContext(), "Configuration Saved", Toast.LENGTH_SHORT).show()
    }
}
