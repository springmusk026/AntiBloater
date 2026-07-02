package com.spring.musk.antibloater.ui.permissions

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
import com.spring.musk.antibloater.databinding.FragmentPermissionsBinding
import kotlinx.coroutines.launch

class PermissionAuditFragment : Fragment() {

    private var _binding: FragmentPermissionsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PermissionAuditViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPermissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = PermGroupAdapter()
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.permissionGroups.collect { groups ->
                        adapter.submitList(groups)
                    }
                }
                launch {
                    viewModel.totalDangerous.collect { total ->
                        binding.summaryText.text = getString(R.string.perm_summary, total)
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

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class PermGroupAdapter : androidx.recyclerview.widget.ListAdapter<PermissionGroup, PermGroupAdapter.VH>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<PermissionGroup>() {
        override fun areItemsTheSame(a: PermissionGroup, b: PermissionGroup) = a.permissionName == b.permissionName
        override fun areContentsTheSame(a: PermissionGroup, b: PermissionGroup) = a == b
    }
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_permission_group, parent, false)
        return VH(view)
    }
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(group: PermissionGroup) {
            itemView.findViewById<TextView>(R.id.perm_name).text = group.friendlyName
            itemView.findViewById<TextView>(R.id.perm_count).text = "${group.count} apps"
            itemView.findViewById<TextView>(R.id.perm_apps).text = group.apps.take(5).joinToString(", ") +
                    if (group.apps.size > 5) " +${group.apps.size - 5} more" else ""
        }
    }
}
