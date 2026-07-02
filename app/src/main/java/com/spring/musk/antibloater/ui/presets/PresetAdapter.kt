package com.spring.musk.antibloater.ui.presets

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.spring.musk.antibloater.databinding.ItemPresetBinding
import com.spring.musk.antibloater.model.OemPresets

class PresetAdapter(
    private val presets: List<OemPresets.Preset>,
    private val onApply: (OemPresets.Preset) -> Unit
) : RecyclerView.Adapter<PresetAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPresetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(presets[position])
    override fun getItemCount() = presets.size

    inner class VH(private val binding: ItemPresetBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(preset: OemPresets.Preset) {
            binding.presetName.text = preset.name
            binding.presetDesc.text = preset.description
            binding.presetCount.text = "${preset.packages.size} apps"
            binding.btnApply.setOnClickListener { onApply(preset) }
        }
    }
}
