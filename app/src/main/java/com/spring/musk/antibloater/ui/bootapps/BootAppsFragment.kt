package com.spring.musk.antibloater.ui.bootapps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.spring.musk.antibloater.R
import com.spring.musk.antibloater.databinding.FragmentBootappsBinding
import kotlinx.coroutines.launch

class BootAppsFragment : Fragment() {

    private var _binding: FragmentBootappsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BootAppsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBootappsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = BootAppAdapter()
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.bootApps.collect { apps ->
                        adapter.submitList(apps)
                        binding.countText.text = getString(R.string.boot_apps_count, apps.size)
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }
            }
        }

        viewModel.load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** Simple adapter for boot apps list */
class BootAppAdapter : androidx.recyclerview.widget.ListAdapter<BootApp, BootAppAdapter.VH>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<BootApp>() {
        override fun areItemsTheSame(a: BootApp, b: BootApp) = a.packageName == b.packageName
        override fun areContentsTheSame(a: BootApp, b: BootApp) = a == b
    }
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(app: BootApp) {
            itemView.findViewById<TextView>(android.R.id.text1).text = app.appName
            itemView.findViewById<TextView>(android.R.id.text2).text =
                "${app.packageName}${if (app.isSystem) " · System" else ""}"
        }
    }
}
