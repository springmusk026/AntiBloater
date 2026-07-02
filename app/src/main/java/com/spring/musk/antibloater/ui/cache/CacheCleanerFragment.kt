package com.spring.musk.antibloater.ui.cache

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.spring.musk.antibloater.R
import com.spring.musk.antibloater.databinding.FragmentCacheBinding
import com.spring.musk.antibloater.model.AdbShellManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CacheInfo(val appName: String, val packageName: String, val cacheBytes: Long) {
    val formatted: String get() = when {
        cacheBytes < 1024 -> "$cacheBytes B"
        cacheBytes < 1024 * 1024 -> "${"%.1f".format(cacheBytes / 1024.0)} KB"
        else -> "${"%.1f".format(cacheBytes / (1024.0 * 1024.0))} MB"
    }
}

class CacheViewModel(application: Application) : AndroidViewModel(application) {
    private val pm = application.packageManager
    private val _caches = MutableStateFlow<List<CacheInfo>>(emptyList())
    val caches: StateFlow<List<CacheInfo>> = _caches.asStateFlow()
    private val _totalCache = MutableStateFlow("")
    val totalCache: StateFlow<String> = _totalCache.asStateFlow()

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                pm.getInstalledApplications(0)
            }

            val cacheList = packages.mapNotNull { appInfo ->
                val cacheDir = java.io.File(appInfo.dataDir + "/cache")
                val cacheSize = if (cacheDir.exists()) dirSize(cacheDir) else 0L
                if (cacheSize > 1024) { // Only show > 1KB
                    CacheInfo(pm.getApplicationLabel(appInfo).toString(), appInfo.packageName, cacheSize)
                } else null
            }.sortedByDescending { it.cacheBytes }

            val total = cacheList.sumOf { it.cacheBytes }
            _totalCache.value = "${"%.1f".format(total / (1024.0 * 1024.0))} MB"
            _caches.value = cacheList
        }
    }

    private fun dirSize(dir: java.io.File): Long {
        var size = 0L
        try {
            dir.walkTopDown().forEach { file ->
                if (file.isFile) size += file.length()
            }
        } catch (_: Exception) {}
        return size
    }
}

@RequiresApi(Build.VERSION_CODES.O)
class CacheCleanerFragment : Fragment() {
    private var _binding: FragmentCacheBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CacheViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCacheBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = CacheAdapter { cache -> clearCache(cache) }
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        binding.btnClearAll.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val shell = AdbShellManager.getInstance(requireContext())
                shell.sendCommand("pm trim-caches 999G")
            }
            Snackbar.make(binding.root, R.string.cache_cleared, Snackbar.LENGTH_SHORT).show()
            viewModel.load()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.caches.collect { adapter.submitList(it) } }
                launch { viewModel.totalCache.collect { binding.totalText.text = getString(R.string.cache_total, it) } }
            }
        }
        viewModel.load()
    }

    private fun clearCache(cache: CacheInfo) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            AdbShellManager.getInstance(requireContext()).sendCommand("pm clear --cache-only ${cache.packageName}")
        }
        Snackbar.make(binding.root, "Cleared ${cache.appName} cache", Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class CacheAdapter(private val onClear: (CacheInfo) -> Unit) :
    androidx.recyclerview.widget.ListAdapter<CacheInfo, CacheAdapter.VH>(
        object : androidx.recyclerview.widget.DiffUtil.ItemCallback<CacheInfo>() {
            override fun areItemsTheSame(a: CacheInfo, b: CacheInfo) = a.packageName == b.packageName
            override fun areContentsTheSame(a: CacheInfo, b: CacheInfo) = a == b
        }
    ) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cache, parent, false)
        return VH(view)
    }
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(cache: CacheInfo) {
            itemView.findViewById<TextView>(R.id.app_name).text = cache.appName
            itemView.findViewById<TextView>(R.id.cache_size).text = cache.formatted
            itemView.setOnClickListener { onClear(cache) }
        }
    }
}
