package com.example.aquatics

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.aquatics.mqtt.MqttManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class StatsFragment : Fragment() {

    private lateinit var txtSoilMoisture: TextView
    private lateinit var txtWaterFlow: TextView
    private lateinit var txtSignal: TextView
    private lateinit var txtUptime: TextView
    private lateinit var txtDryRunStatus: TextView
    private lateinit var txtStatus: TextView
    private lateinit var statusIndicator: View
    private lateinit var btnViewLogs: MaterialButton
    private lateinit var dryRunAlertCard: MaterialCardView
    
    private lateinit var mqttStatusDot: View
    private lateinit var txtMqttStatus: TextView

    private val viewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_stats, container, false)

        txtSoilMoisture = view.findViewById(R.id.txtSoilMoisture)
        txtWaterFlow = view.findViewById(R.id.txtWaterFlow)
        txtSignal = view.findViewById(R.id.txtSignal)
        txtUptime = view.findViewById(R.id.txtUptime)
        txtDryRunStatus = view.findViewById(R.id.txtDryRunStatus)
        txtStatus = view.findViewById(R.id.txtStatus)
        statusIndicator = view.findViewById(R.id.statusIndicator)
        btnViewLogs = view.findViewById(R.id.btnViewLogs)
        dryRunAlertCard = view.findViewById(R.id.dryRunAlertCard)
        
        mqttStatusDot = view.findViewById(R.id.mqttStatusDot)
        txtMqttStatus = view.findViewById(R.id.txtMqttStatus)

        btnViewLogs.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, LogsFragment())
                .addToBackStack(null)
                .commit()
        }

        setupMqttStatusListener()
        observeViewModel()

        AppLogger.info(requireContext(), "StatsFragment", "User opened statistics screen")

        return view
    }

    private fun observeViewModel() {
        viewModel.sensorData.observe(viewLifecycleOwner) { data ->
            if (data != null) {
                updateSensorUI(data)
            } else {
                setInitialValuesToNA()
            }
        }
    }

    private fun setInitialValuesToNA() {
        txtSoilMoisture.text = "NA"
        txtWaterFlow.text = "NA"
        txtSignal.text = "NA"
        txtUptime.text = "NA"
        txtStatus.text = "ERROR"
        statusIndicator.setBackgroundColor(resources.getColor(android.R.color.holo_red_light, null))
    }

    private fun setupMqttStatusListener() {
        MqttManager.getInstance(requireContext()).setOnConnectionStatusChangedListener { isConnected ->
            activity?.runOnUiThread {
                if (isConnected) {
                    mqttStatusDot.setBackgroundColor(resources.getColor(android.R.color.holo_green_light, null))
                    txtMqttStatus.text = "Server Connection: Connected"
                } else {
                    mqttStatusDot.setBackgroundColor(resources.getColor(android.R.color.holo_red_dark, null))
                    txtMqttStatus.text = "Server Connection: Disconnected"
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSensorPlaceholders()
    }

    private fun refreshSensorPlaceholders() {
        val sharedPref = requireActivity().getSharedPreferences("AquaticsSettings", Context.MODE_PRIVATE)
        val flowEnabled = sharedPref.getBoolean("sensor_flow", false)
        val soilEnabled = sharedPref.getBoolean("sensor_soil", true)

        if (!flowEnabled && txtWaterFlow.text == "NA") {
            txtWaterFlow.setTextColor(resources.getColor(android.R.color.darker_gray, null))
        }
        if (!soilEnabled && txtSoilMoisture.text == "NA") {
            txtSoilMoisture.setTextColor(resources.getColor(android.R.color.darker_gray, null))
        }
    }

    private fun updateSensorUI(data: SensorData) {
        val context = context ?: return
        val sharedPref = context.getSharedPreferences("AquaticsSettings", Context.MODE_PRIVATE)
        val soilEnabled = sharedPref.getBoolean("sensor_soil", true)
        val flowEnabled = sharedPref.getBoolean("sensor_flow", false)

        txtSoilMoisture.text = if (soilEnabled && data.soil != "NA") data.soil else "NA"
        txtWaterFlow.text = if (flowEnabled && data.flow != "NA") data.flow else "NA"
        
        txtSignal.text = data.signal
        txtUptime.text = data.uptime
        
        if (data.isDryRunning && flowEnabled) {
            dryRunAlertCard.visibility = View.VISIBLE
            txtDryRunStatus.text = "ERROR"
            txtDryRunStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            NotificationHelper.showAlert(context, "Critical Error", "Dry run detected! Pump stopped.", true)
        } else {
            dryRunAlertCard.visibility = View.GONE
            txtDryRunStatus.text = data.dryRunString
            if (data.dryRunString == "OK") {
                txtDryRunStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            } else {
                txtDryRunStatus.setTextColor(resources.getColor(android.R.color.darker_gray, null))
            }
        }
        
        when(data.online) {
            true -> {
                txtStatus.text = "OK"
                statusIndicator.setBackgroundColor(resources.getColor(android.R.color.holo_green_light, null))
            }
            false -> {
                txtStatus.text = "ERROR"
                statusIndicator.setBackgroundColor(resources.getColor(android.R.color.holo_red_light, null))
            }
            null -> {
                txtStatus.text = "--"
                statusIndicator.setBackgroundColor(resources.getColor(android.R.color.darker_gray, null))
            }
        }
    }
}
