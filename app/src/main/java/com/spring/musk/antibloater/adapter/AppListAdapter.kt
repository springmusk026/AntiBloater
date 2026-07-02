package com.spring.musk.antibloater.adapter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.spring.musk.antibloater.R
import com.spring.musk.antibloater.databinding.ListItemAppBinding
import com.spring.musk.antibloater.model.AppCategory
import com.spring.musk.antibloater.model.AppInfo
import com.spring.musk.antibloater.model.SafetyRating

/**
 * High-performance RecyclerView adapter.
 *
 * Optimizations:
 *  - Icons pre-cached in AppInfo (no PackageManager calls during scroll)
 *  - Tag backgrounds pre-allocated as static drawables (no GC in bind)
 *  - Colors pre-parsed as constants
 *  - Thread-safe selection with HashSet
 */
class AppListAdapter(
    private val onSelectionChanged: (selectedCount: Int) -> Unit
) : ListAdapter<AppInfo, AppListAdapter.AppViewHolder>(AppDiffCallback()) {

    private val selectedPackages = HashSet<String>()
    private val selectionLock = Any()

    // Pre-parsed colors (avoid Color.parseColor in bind)
    companion object {
        private val COLOR_BLOAT_TEXT = Color.parseColor("#E65100")
        private val COLOR_BLOAT_BG = Color.parseColor("#FFF3E0")
        private val COLOR_SYSTEM_TEXT = Color.parseColor("#1565C0")
        private val COLOR_SYSTEM_BG = Color.parseColor("#E3F2FD")
        private val COLOR_USER_TEXT = Color.parseColor("#2E7D32")
        private val COLOR_USER_BG = Color.parseColor("#E8F5E9")
        private val COLOR_DANGER_TEXT = Color.parseColor("#B71C1C")
        private val COLOR_DANGER_BG = Color.parseColor("#FFEBEE")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ListItemAppBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun getSelectedApps(): List<AppInfo> {
        val selected = synchronized(selectionLock) { selectedPackages.toSet() }
        return currentList.filter { it.packageName in selected }
    }

    fun getSelectedCount(): Int = synchronized(selectionLock) { selectedPackages.size }

    fun clearSelections() {
        synchronized(selectionLock) { selectedPackages.clear() }
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    inner class AppViewHolder(
        private val binding: ListItemAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        // Pre-allocate tag background (reused across rebinds)
        private val tagBackground = GradientDrawable().apply {
            cornerRadius = 12f
        }

        fun bind(appInfo: AppInfo) {
            binding.appName.text = appInfo.appName

            // Use pre-cached icon (no IPC)
            if (appInfo.icon != null) {
                binding.appIcon.setImageDrawable(appInfo.icon)
            }

            // Category tag with safety-aware coloring
            binding.appCategoryTag.visibility = View.VISIBLE
            when (appInfo.safety) {
                SafetyRating.SAFE -> {
                    binding.appCategoryTag.text = "✓ Safe to remove"
                    binding.appCategoryTag.setTextColor(COLOR_USER_TEXT)
                    tagBackground.setColor(COLOR_USER_BG)
                }
                SafetyRating.CAUTION -> {
                    binding.appCategoryTag.text = "⚠ Caution"
                    binding.appCategoryTag.setTextColor(COLOR_BLOAT_TEXT)
                    tagBackground.setColor(COLOR_BLOAT_BG)
                }
                SafetyRating.DANGEROUS -> {
                    binding.appCategoryTag.text = "✕ Do not remove"
                    binding.appCategoryTag.setTextColor(COLOR_DANGER_TEXT)
                    tagBackground.setColor(COLOR_DANGER_BG)
                }
            }
            binding.appCategoryTag.background = tagBackground

            // Show size in package subtitle
            binding.appPackage.text = "${appInfo.packageName} · ${appInfo.sizeFormatted}"

            // Checkbox (no listener during state set)
            binding.appCheckbox.setOnCheckedChangeListener(null)
            binding.appCheckbox.isChecked = synchronized(selectionLock) {
                appInfo.packageName in selectedPackages
            }

            binding.appCheckbox.setOnCheckedChangeListener { _, isChecked ->
                synchronized(selectionLock) {
                    if (isChecked) selectedPackages.add(appInfo.packageName)
                    else selectedPackages.remove(appInfo.packageName)
                }
                onSelectionChanged(getSelectedCount())
            }

            binding.root.setOnClickListener {
                binding.appCheckbox.isChecked = !binding.appCheckbox.isChecked
            }
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean =
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean =
            oldItem == newItem
    }
}
