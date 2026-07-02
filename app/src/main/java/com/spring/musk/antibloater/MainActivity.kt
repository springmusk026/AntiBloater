package com.spring.musk.antibloater

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.spring.musk.antibloater.databinding.ActivityMainBinding
import com.spring.musk.antibloater.ui.apps.AppsFragment
import com.spring.musk.antibloater.ui.dashboard.DashboardFragment
import com.spring.musk.antibloater.ui.pairing.PairingFragment
import com.spring.musk.antibloater.ui.restore.RestoreFragment
import com.spring.musk.antibloater.ui.settings.SettingsFragment

/**
 * MainActivity — pure navigation router with bottom navigation.
 * Zero business logic. Hosts fragments and routes between them.
 */
@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNav()

        if (savedInstanceState == null) {
            // Start with pairing — it auto-navigates to dashboard on success
            showFragment(PairingFragment())
            binding.bottomNav.visibility = View.GONE
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { showFragment(DashboardFragment()); true }
                R.id.nav_apps -> { showFragment(AppsFragment()); true }
                R.id.nav_restore -> { showFragment(RestoreFragment()); true }
                R.id.nav_settings -> { showFragment(SettingsFragment()); true }
                else -> false
            }
        }
    }

    // ─── Public navigation API for fragments ─────────────────────────────

    fun navigateToDashboard() {
        binding.bottomNav.visibility = View.VISIBLE
        binding.bottomNav.selectedItemId = R.id.nav_home
    }

    fun navigateToPairing() {
        binding.bottomNav.visibility = View.GONE
        showFragment(PairingFragment())
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.commit {
            setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            replace(R.id.fragment_container, fragment)
        }
    }
}
