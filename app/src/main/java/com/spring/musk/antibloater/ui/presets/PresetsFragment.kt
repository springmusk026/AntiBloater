package com.spring.musk.antibloater.ui.presets

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
import com.spring.musk.antibloater.R
import com.spring.musk.antibloater.databinding.FragmentPresetsBinding
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class PresetsFragment : Fragment() {

    private var _binding: FragmentPresetsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PresetsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPresetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = PresetAdapter(viewModel.presets) { preset ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Apply ${preset.name} preset?")
                .setMessage("This will remove ${preset.packages.size} known bloatware apps. Only apps actually installed on your device will be affected.")
                .setPositiveButton(R.string.preset_apply) { _, _ -> viewModel.applyPreset(preset) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Show detected device
        viewModel.detectedPreset?.let { detected ->
            binding.detectedText.visibility = View.VISIBLE
            binding.detectedText.text = getString(R.string.preset_detected, detected.name)
        }

        // Observe state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is PresetUiState.Idle -> {
                            binding.progress.visibility = View.GONE
                            binding.statusText.visibility = View.GONE
                        }
                        is PresetUiState.Applying -> {
                            binding.progress.visibility = View.VISIBLE
                            binding.statusText.visibility = View.VISIBLE
                            binding.statusText.text = getString(R.string.uninstalling_progress, state.current, state.total)
                        }
                        is PresetUiState.Done -> {
                            binding.progress.visibility = View.GONE
                            binding.statusText.visibility = View.VISIBLE
                            binding.statusText.text = getString(R.string.preset_done, state.count)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
