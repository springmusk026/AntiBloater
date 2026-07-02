package com.spring.musk.antibloater.ui.restore

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.spring.musk.antibloater.databinding.ItemRestoreBinding
import com.spring.musk.antibloater.model.RemovedAppsRepository

class RestoreAdapter(
    private val onRestore: (RemovedAppsRepository.RemovedApp) -> Unit
) : ListAdapter<RemovedAppsRepository.RemovedApp, RestoreAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRestoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemRestoreBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: RemovedAppsRepository.RemovedApp) {
            binding.appName.text = app.appName
            binding.appPackage.text = app.packageName
            binding.btnRestore.setOnClickListener { onRestore(app) }
        }
    }

    class Diff : DiffUtil.ItemCallback<RemovedAppsRepository.RemovedApp>() {
        override fun areItemsTheSame(a: RemovedAppsRepository.RemovedApp, b: RemovedAppsRepository.RemovedApp) =
            a.packageName == b.packageName
        override fun areContentsTheSame(a: RemovedAppsRepository.RemovedApp, b: RemovedAppsRepository.RemovedApp) =
            a == b
    }
}
