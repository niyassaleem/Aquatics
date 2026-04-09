package com.example.aquatics

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Create notification channel
        NotificationHelper.createNotificationChannel(this)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        // Load default fragment
        loadFragment(HomeFragment())

        bottomNav.setOnItemSelectedListener {

            when (it.itemId) {

                R.id.nav_home -> loadFragment(HomeFragment())

                R.id.nav_stats -> loadFragment(StatsFragment())

                R.id.nav_settings -> loadFragment(SettingsFragment())

            }

            true
        }


    }

    private fun loadFragment(fragment: Fragment) {

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.container, fragment)
            .commit()

    }


}