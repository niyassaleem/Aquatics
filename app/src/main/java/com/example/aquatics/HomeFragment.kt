package com.example.aquatics

import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.aquatics.mqtt.MqttManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.*
import java.util.UUID

class HomeFragment : Fragment() {

    private lateinit var pumpCard: MaterialCardView
    private lateinit var irrigationCard: MaterialCardView
    private lateinit var pumpState: TextView
    private lateinit var irrigationState: TextView
    private lateinit var pumpTimerText: TextView
    private lateinit var irrigationTimerText: TextView
    private lateinit var pumpIcon: ImageView
    private lateinit var irrigationIcon: ImageView
    private lateinit var tankGauge: CircularProgressIndicator
    private lateinit var tankPercent: TextView
    private lateinit var weatherIcon: ImageView
    private lateinit var weatherTemp: TextView
    private lateinit var weatherCondition: TextView
    private lateinit var waterUsageText: TextView
    private lateinit var dashboardContent: View

    private var rotationAnimation: Animation? = null

    private val viewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        dashboardContent = view.findViewById(R.id.dashboardContent)
        pumpCard = view.findViewById(R.id.pumpCard)
        irrigationCard = view.findViewById(R.id.irrigationCard)
        pumpState = view.findViewById(R.id.pumpState)
        irrigationState = view.findViewById(R.id.irrigationState)
        pumpTimerText = view.findViewById(R.id.pumpTimer)
        irrigationTimerText = view.findViewById(R.id.irrigationTimer)
        pumpIcon = view.findViewById(R.id.pumpIcon)
        irrigationIcon = view.findViewById(R.id.irrigationIcon)
        tankGauge = view.findViewById(R.id.tankGauge)
        tankPercent = view.findViewById(R.id.tankPercent)
        weatherIcon = view.findViewById(R.id.weatherIcon)
        weatherTemp = view.findViewById(R.id.weatherTemp)
        weatherCondition = view.findViewById(R.id.weatherCondition)
        waterUsageText = view.findViewById(R.id.waterUsageValue)

        rotationAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.rotate_propeller)

        // Fade in dashboard
        dashboardContent.animate()
            .alpha(1f)
            .setDuration(800)
            .setStartDelay(200)
            .start()

        observeViewModel()

        pumpCard.setOnClickListener { viewModel.togglePump() }
        irrigationCard.setOnClickListener { viewModel.toggleIrrigation() }

        // Initialize with default values
        tankGauge.progress = 0
        tankPercent.text = "--"
        waterUsageText.text = "-- Liters"

        return view
    }

    private fun observeViewModel() {
        viewModel.pumpRunning.observe(viewLifecycleOwner) { isRunning ->
            if (isRunning) {
                pumpState.text = "ON"
                pumpState.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
                pumpIcon.startAnimation(rotationAnimation)
            } else {
                pumpState.text = "OFF"
                pumpState.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                pumpIcon.clearAnimation()
            }
        }

        viewModel.irrigationRunning.observe(viewLifecycleOwner) { isRunning ->
            val drawable = irrigationIcon.drawable as? AnimatedVectorDrawable
            if (isRunning) {
                irrigationState.text = "ON"
                irrigationState.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark))
                irrigationIcon.alpha = 1.0f
                drawable?.start()
            } else {
                irrigationState.text = "OFF"
                irrigationState.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                irrigationIcon.alpha = 0.6f
                drawable?.stop()
            }
        }

        viewModel.pumpTimeText.observe(viewLifecycleOwner) { pumpTimerText.text = it }
        viewModel.irrigationTimeText.observe(viewLifecycleOwner) { irrigationTimerText.text = it }

        viewModel.sensorData.observe(viewLifecycleOwner) { data ->
            data?.let {
                tankGauge.progress = it.level.replace("%", "").toIntOrNull() ?: 0
                tankPercent.text = it.level
                waterUsageText.text = it.usage
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshTimers()
        refreshWeather()
        viewModel.startConnectionCycle()
    }

    private fun refreshWeather() {
        val sharedPref = requireContext().getSharedPreferences("AquaticsSettings", Context.MODE_PRIVATE)
        val city = sharedPref.getString("weather_city", "Kochi") ?: "Kochi"
        val apiKey = sharedPref.getString("weather_api_key", "") ?: ""
        if (apiKey.isNotEmpty()) fetchWeatherData(city, apiKey)
        else weatherCondition.text = "Set API Key"
    }

    private fun fetchWeatherData(city: String, apiKey: String) {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { WeatherClient.api.getWeather(city, apiKey) }
                if (!isAdded) return@launch
                val temp = response.main.temp.toInt()
                val condition = response.weather.firstOrNull()?.main ?: "Sunny"
                weatherTemp.text = "$temp°C"
                weatherCondition.text = condition
                
                // Night detection logic using sunset/sunrise
                val currentTime = System.currentTimeMillis() / 1000
                val isNight = currentTime < response.sys.sunrise || currentTime > response.sys.sunset

                when (condition.lowercase()) {
                    "clear" -> {
                        if (isNight) weatherIcon.setImageResource(R.drawable.ic_moon)
                        else weatherIcon.setImageResource(R.drawable.ic_sun)
                    }
                    "thunderstorm", "drizzle", "rain" -> weatherIcon.setImageResource(R.drawable.ic_rain)
                    "snow" -> weatherIcon.setImageResource(R.drawable.ic_snow)
                    "clouds", "mist", "smoke", "haze", "dust", "fog", "sand", "ash", "squall", "tornado" -> 
                        weatherIcon.setImageResource(R.drawable.ic_cloud)
                    else -> weatherIcon.setImageResource(R.drawable.ic_sun)
                }
            } catch (e: Exception) { 
                if (isAdded) weatherCondition.text = "Offline"
            }
        }
    }
}
