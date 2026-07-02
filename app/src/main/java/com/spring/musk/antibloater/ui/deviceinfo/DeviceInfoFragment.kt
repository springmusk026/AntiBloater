package com.spring.musk.antibloater.ui.deviceinfo

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.spring.musk.antibloater.databinding.FragmentDeviceinfoBinding

class DeviceInfoFragment : Fragment() {

    private var _binding: FragmentDeviceinfoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeviceinfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Device
        binding.deviceModel.text = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL}"
        binding.androidVersion.text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        binding.securityPatch.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A"
        binding.kernel.text = System.getProperty("os.version") ?: "Unknown"

        // CPU
        binding.cpuArch.text = Build.SUPPORTED_ABIS.joinToString(", ")
        binding.cpuCores.text = "${Runtime.getRuntime().availableProcessors()} cores"

        // RAM
        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRam = memInfo.totalMem / (1024 * 1024)
        val availRam = memInfo.availMem / (1024 * 1024)
        binding.ramTotal.text = "${totalRam} MB"
        binding.ramAvailable.text = "${availRam} MB free"

        // Storage
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalStorage = stat.totalBytes / (1024 * 1024 * 1024)
        val freeStorage = stat.availableBytes / (1024 * 1024 * 1024)
        binding.storageTotal.text = "${totalStorage} GB"
        binding.storageFree.text = "${freeStorage} GB free"
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
