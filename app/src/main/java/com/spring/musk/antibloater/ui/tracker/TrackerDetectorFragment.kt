package com.spring.musk.antibloater.ui.tracker

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
import com.spring.musk.antibloater.databinding.FragmentTrackerBinding
import kotlinx.coroutines.launch

class TrackerDetectorFragment : Fragment() {

    private var _binding: FragmentTrackerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TrackerDetectorViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTrackerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = TrackerAdapter()
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.results.collect { adapter.submitList(it) } }
                launch { viewModel.averageScore.collect { binding.scoreText.text = getString(R.string.tracker_score, it) } }
                launch { viewModel.isLoading.collect { binding.progress.visibility = if (it) View.VISIBLE else View.GONE } }
            }
        }
        viewModel.scan()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class TrackerAdapter : androidx.recyclerview.widget.ListAdapter<TrackerResult, TrackerAdapter.VH>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<TrackerResult>() {
        override fun areItemsTheSame(a: TrackerResult, b: TrackerResult) = a.packageName == b.packageName
        override fun areContentsTheSame(a: TrackerResult, b: TrackerResult) = a == b
    }
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tracker, parent, false)
        return VH(view)
    }
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(result: TrackerResult) {
            itemView.findViewById<TextView>(R.id.app_name).text = result.appName
            itemView.findViewById<TextView>(R.id.tracker_list).text = result.trackers.joinToString(" · ")
            itemView.findViewById<TextView>(R.id.privacy_score).text = "Score: ${result.score}/100"
        }
    }
}
