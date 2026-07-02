package com.spring.musk.antibloater.ui.pairing

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.spring.musk.antibloater.MainActivity
import com.spring.musk.antibloater.R
import com.spring.musk.antibloater.databinding.FragmentPairingBinding
import com.spring.musk.antibloater.service.PairingNotificationManager
import com.spring.musk.antibloater.service.PairingReceiver
import kotlinx.coroutines.launch

/**
 * PairingFragment — completely self-contained pairing module.
 * Owns: pairing UI, notification, settings launch, connection check.
 * Navigates to Dashboard on success.
 */
@RequiresApi(Build.VERSION_CODES.O)
class PairingFragment : Fragment() {

    private var _binding: FragmentPairingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PairingViewModel by viewModels()
    private lateinit var notificationManager: PairingNotificationManager

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchSettingsWithNotification()
        else openSettings()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPairingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        notificationManager = PairingNotificationManager(requireContext())

        setupButtons()
        setupNotificationReceiver()
        observeState()

        viewModel.checkConnectionStatus()
    }

    override fun onResume() {
        super.onResume()
        // Auto-focus when returning from Settings
        if (binding.inputPort.text.isNullOrBlank()) {
            binding.inputPort.requestFocus()
        } else if (binding.inputCode.text.isNullOrBlank()) {
            binding.inputCode.requestFocus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        PairingReceiver.onPairingInput = null
        PairingReceiver.onDismiss = null
        notificationManager.dismiss()
        _binding = null
    }

    private fun setupButtons() {
        binding.btnConnect.setOnClickListener {
            val port = binding.inputPort.text.toString().trim()
            val code = binding.inputCode.text.toString().trim()
            if (port.isBlank() || code.isBlank()) {
                Snackbar.make(binding.root, "Enter both port and code", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            notificationManager.dismiss()
            viewModel.pair(port, code)
        }

        binding.btnOpenSettings.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                launchSettingsWithNotification()
            }
        }

        binding.btnSkip.setOnClickListener {
            notificationManager.dismiss()
            viewModel.skip()
        }
    }

    private fun setupNotificationReceiver() {
        PairingReceiver.onPairingInput = { port, code ->
            activity?.runOnUiThread {
                binding.inputPort.setText(port)
                binding.inputCode.setText(code)
                viewModel.pair(port, code)
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is PairingState.Checking -> showLoading(true)
                        is PairingState.Required -> showPairingUI()
                        is PairingState.Connecting -> showConnecting()
                        is PairingState.Connected -> navigateToDashboard()
                        is PairingState.Failed -> showError(state.message)
                    }
                }
            }
        }
    }

    private fun showPairingUI() {
        binding.pairingContent.visibility = View.VISIBLE
        binding.progress.visibility = View.GONE
        binding.btnConnect.isEnabled = true
        binding.btnConnect.text = getString(R.string.pair)
    }

    private fun showConnecting() {
        binding.pairingContent.visibility = View.GONE
        binding.progress.visibility = View.VISIBLE
    }

    private fun showLoading(show: Boolean) {
        binding.pairingContent.visibility = View.GONE
        binding.progress.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        binding.pairingContent.visibility = View.VISIBLE
        binding.progress.visibility = View.GONE
        binding.btnConnect.isEnabled = true
        binding.btnConnect.text = getString(R.string.pair)
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun navigateToDashboard() {
        (activity as? MainActivity)?.navigateToDashboard()
    }

    private fun launchSettingsWithNotification() {
        notificationManager.showPairingNotification()
        openSettings()
    }

    private fun openSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            catch (_: Exception) { }
        }
    }
}
