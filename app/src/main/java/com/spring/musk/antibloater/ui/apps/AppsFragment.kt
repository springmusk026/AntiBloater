package com.spring.musk.antibloater.ui.apps

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.spring.musk.antibloater.R
import com.spring.musk.antibloater.adapter.AppListAdapter
import com.spring.musk.antibloater.databinding.FragmentAppsBinding
import com.spring.musk.antibloater.model.AppCategory
import kotlinx.coroutines.launch

/**
 * AppsFragment — fully self-contained app management screen.
 *
 * Owns: RecyclerView, filtering, chip selection, search, uninstall confirmation + execution.
 * Knows nothing about pairing, dashboard, navigation structure, or any other feature.
 * Can be dropped into any activity/nav graph without changes.
 */
@RequiresApi(Build.VERSION_CODES.O)
class AppsFragment : Fragment() {

    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppsViewModel by viewModels()
    private lateinit var adapter: AppListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupChips()
        setupSearch()
        setupFab()
        observeState()

        viewModel.loadApps()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─── Setup ───────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = AppListAdapter { selectedCount ->
            updateFab(selectedCount)
        }
        binding.recyclerView.apply {
            this.adapter = this@AppsFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
        }
    }

    private fun setupChips() {
        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val category = when {
                checkedIds.contains(R.id.chip_bloatware) -> AppCategory.BLOATWARE
                checkedIds.contains(R.id.chip_system) -> AppCategory.SYSTEM
                checkedIds.contains(R.id.chip_user) -> AppCategory.USER
                else -> null
            }
            viewModel.setCategory(category)
        }
    }

    private fun setupSearch() {
        binding.searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
        })

        binding.switchMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setDisableMode(isChecked)
        }
    }

    private fun setupFab() {
        binding.fabUninstall.setOnClickListener {
            val selected = adapter.getSelectedApps()
            if (selected.isEmpty()) {
                Snackbar.make(binding.root, R.string.no_apps_selected, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            confirmUninstall(selected.size)
        }
    }

    // ─── State ───────────────────────────────────────────────────────────

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectApps() }
                launch { collectUiState() }
            }
        }
    }

    private suspend fun collectApps() {
        viewModel.apps.collect { apps ->
            adapter.submitList(apps)
            binding.appCount.text = getString(R.string.app_count_format, apps.size, adapter.getSelectedCount())
        }
    }

    private suspend fun collectUiState() {
        viewModel.uiState.collect { state ->
            when (state) {
                is AppsUiState.Loading -> {
                    binding.fabUninstall.isEnabled = false
                }
                is AppsUiState.Ready -> {
                    binding.fabUninstall.isEnabled = true
                    binding.fabUninstall.text = getString(R.string.start_debloating)
                }
                is AppsUiState.Uninstalling -> {
                    binding.fabUninstall.isEnabled = false
                    binding.fabUninstall.text = getString(R.string.uninstalling_progress, state.current, state.total)
                }
                is AppsUiState.UninstallComplete -> {
                    binding.fabUninstall.isEnabled = false
                    val sizeStr = formatSize(state.freedBytes)
                    Snackbar.make(
                        binding.root,
                        getString(R.string.uninstall_complete, state.removedCount, sizeStr),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 * 1024 -> "${"%.0f".format(bytes / 1024.0)} KB"
            bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }

    // ─── UI Helpers ──────────────────────────────────────────────────────

    private fun updateFab(selectedCount: Int) {
        if (selectedCount > 0) {
            binding.fabUninstall.visibility = View.VISIBLE
            binding.fabUninstall.text = "${getString(R.string.start_debloating)} ($selectedCount)"
            binding.fabUninstall.extend()
        } else {
            binding.fabUninstall.shrink()
        }
        binding.appCount.text = getString(R.string.app_count_format, adapter.currentList.size, selectedCount)
    }

    private fun confirmUninstall(count: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_uninstall_title)
            .setMessage(getString(R.string.confirm_uninstall_message, count))
            .setPositiveButton(R.string.uninstall) { _, _ ->
                val apps = adapter.getSelectedApps()
                adapter.clearSelections()
                viewModel.uninstallApps(apps)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
