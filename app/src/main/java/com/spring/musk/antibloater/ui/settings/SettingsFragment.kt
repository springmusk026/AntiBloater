package com.spring.musk.antibloater.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.spring.musk.antibloater.R
import com.spring.musk.antibloater.databinding.FragmentSettingsBinding
import com.spring.musk.antibloater.model.RemovedAppsRepository

/**
 * SettingsFragment — About, links, legal pages.
 * Enterprise-grade settings screen with proper legal compliance.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Dark mode toggle
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val isDark = prefs.getBoolean("dark_mode", false)
        binding.switchDarkMode.isChecked = isDark
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        // Links
        binding.itemGithub.setOnClickListener {
            openUrl(getString(R.string.url_github))
        }

        binding.itemRate.setOnClickListener {
            openUrl(getString(R.string.url_playstore))
        }

        binding.itemShare.setOnClickListener {
            shareApp()
        }

        // Legal
        binding.itemTos.setOnClickListener {
            openUrl(getString(R.string.url_tos))
        }

        binding.itemPrivacy.setOnClickListener {
            openUrl(getString(R.string.url_privacy))
        }

        binding.itemLicenses.setOnClickListener {
            showLicensesDialog()
        }

        // Profiles
        binding.itemExport.setOnClickListener {
            exportProfile()
        }

        binding.itemImport.setOnClickListener {
            Snackbar.make(binding.root, R.string.import_hint, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            // No browser available
        }
    }

    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text))
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.settings_share)))
    }

    private fun showLicensesDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_licenses)
            .setMessage(R.string.licenses_content)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun exportProfile() {
        val repo = RemovedAppsRepository(requireContext())
        val json = repo.exportProfile()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, json)
            putExtra(Intent.EXTRA_SUBJECT, "AntiBloater Debloat Profile")
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.settings_export)))
    }
}
