package com.spring.musk.antibloater.ui.processes

import android.app.ActivityManager
import android.app.Application
import android.content.Context
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.spring.musk.antibloater.R
import com.spring.musk.antibloater.databinding.FragmentProcessesBinding
import com.spring.musk.antibloater.model.AdbShellManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RunningProcess(
    val processName: String,
    val pid: Int,
    val memoryKb: Int
) {
    val memoryFormatted: String get() = "${"%.1f".format(memoryKb / 1024.0)} MB"
}

class ProcessViewModel(application: Application) : AndroidViewModel(application) {
    private val am = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val _processes = MutableStateFlow<List<RunningProcess>>(emptyList())
    val processes: StateFlow<List<RunningProcess>> = _processes.asStateFlow()

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            val running = am.runningAppProcesses ?: emptyList()
            val procs = running.map { proc ->
                val mem = am.getProcessMemoryInfo(intArrayOf(proc.pid))
                RunningProcess(
                    processName = proc.processName,
                    pid = proc.pid,
                    memoryKb = mem.firstOrNull()?.totalPss ?: 0
                )
            }.sortedByDescending { it.memoryKb }

            _processes.value = procs
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
class ProcessMonitorFragment : Fragment() {
    private var _binding: FragmentProcessesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProcessViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProcessesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = ProcessAdapter { proc -> forceStop(proc) }
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.processes.collect { procs ->
                    adapter.submitList(procs)
                    binding.countText.text = getString(R.string.process_count, procs.size)
                }
            }
        }
        viewModel.load()
    }

    private fun forceStop(proc: RunningProcess) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            AdbShellManager.getInstance(requireContext()).sendCommand("am force-stop ${proc.processName}")
        }
        Snackbar.make(binding.root, "Force stopped ${proc.processName}", Snackbar.LENGTH_SHORT).show()
        viewModel.load()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class ProcessAdapter(private val onForceStop: (RunningProcess) -> Unit) :
    androidx.recyclerview.widget.ListAdapter<RunningProcess, ProcessAdapter.VH>(
        object : androidx.recyclerview.widget.DiffUtil.ItemCallback<RunningProcess>() {
            override fun areItemsTheSame(a: RunningProcess, b: RunningProcess) = a.pid == b.pid
            override fun areContentsTheSame(a: RunningProcess, b: RunningProcess) = a == b
        }
    ) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_process, parent, false)
        return VH(view)
    }
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(proc: RunningProcess) {
            itemView.findViewById<TextView>(R.id.proc_name).text = proc.processName
            itemView.findViewById<TextView>(R.id.proc_memory).text = proc.memoryFormatted
            itemView.findViewById<MaterialButton>(R.id.btn_stop).setOnClickListener { onForceStop(proc) }
        }
    }
}
