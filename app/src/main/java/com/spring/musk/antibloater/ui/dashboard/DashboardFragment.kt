package com.spring.musk.antibloater.ui.dashboard

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.spring.musk.antibloater.MainActivity
import com.spring.musk.antibloater.R
import com.spring.musk.antibloater.databinding.FragmentDashboardBinding
import com.spring.musk.antibloater.model.AdbConnectionManager
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateConnectionStatus()

        // Quick actions
        binding.btnManageApps.setOnClickListener {
            (activity as? MainActivity)?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(
                R.id.bottom_nav
            )?.selectedItemId = R.id.nav_apps
        }

        binding.btnPresets.setOnClickListener {
            navigateToFragment(com.spring.musk.antibloater.ui.presets.PresetsFragment())
        }

        binding.btnBootApps.setOnClickListener {
            navigateToFragment(com.spring.musk.antibloater.ui.bootapps.BootAppsFragment())
        }

        // Tools
        binding.btnPermissions.setOnClickListener {
            navigateToFragment(com.spring.musk.antibloater.ui.permissions.PermissionAuditFragment())
        }

        binding.btnTracker.setOnClickListener {
            navigateToFragment(com.spring.musk.antibloater.ui.tracker.TrackerDetectorFragment())
        }

        binding.btnAnimations.setOnClickListener {
            navigateToFragment(com.spring.musk.antibloater.ui.animations.AnimationTweakFragment())
        }

        binding.btnCache.setOnClickListener {
            navigateToFragment(com.spring.musk.antibloater.ui.cache.CacheCleanerFragment())
        }

        binding.btnProcesses.setOnClickListener {
            navigateToFragment(com.spring.musk.antibloater.ui.processes.ProcessMonitorFragment())
        }

        binding.btnDeviceInfo.setOnClickListener {
            navigateToFragment(com.spring.musk.antibloater.ui.deviceinfo.DeviceInfoFragment())
        }

        observeStats()
        viewModel.loadStats()
    }

    override fun onResume() {
        super.onResume()
        updateConnectionStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateConnectionStatus() {
        val connected = AdbConnectionManager.getInstance(requireContext()).isConnected()
        if (connected) {
            binding.connectionCard.setCardBackgroundColor(Color.parseColor("#1B5E20"))
            binding.connectionText.text = getString(R.string.status_connected)
            binding.connectionDot.setBackgroundResource(R.drawable.circle_green)
            binding.connectionCard.setOnClickListener(null)
        } else {
            binding.connectionCard.setCardBackgroundColor(Color.parseColor("#B71C1C"))
            binding.connectionText.text = getString(R.string.status_tap_to_connect)
            binding.connectionDot.setBackgroundResource(R.drawable.circle_red)
            binding.connectionCard.setOnClickListener {
                (activity as? MainActivity)?.navigateToPairing()
            }
        }
    }

    private fun navigateToFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun observeStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.stats.collect { stats ->
                    binding.statTotal.text = stats.totalApps.toString()
                    binding.statBloat.text = stats.bloatwareApps.toString()
                    binding.statSystem.text = stats.systemApps.toString()
                    binding.statUser.text = stats.userApps.toString()
                }
            }
        }
    }
}
