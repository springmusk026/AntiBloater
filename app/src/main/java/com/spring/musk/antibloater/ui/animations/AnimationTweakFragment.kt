package com.spring.musk.antibloater.ui.animations

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.spring.musk.antibloater.R
import com.spring.musk.antibloater.databinding.FragmentAnimationsBinding
import com.spring.musk.antibloater.model.AdbShellManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class AnimationTweakFragment : Fragment() {

    private var _binding: FragmentAnimationsBinding? = null
    private val binding get() = _binding!!
    private val shellManager by lazy { AdbShellManager.getInstance(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAnimationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSpeed05.setOnClickListener { setAnimationScale(0.5f) }
        binding.btnSpeed1.setOnClickListener { setAnimationScale(1.0f) }
        binding.btnSpeedOff.setOnClickListener { setAnimationScale(0.0f) }
    }

    private fun setAnimationScale(scale: Float) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            shellManager.sendCommand("settings put global window_animation_scale $scale")
            shellManager.sendCommand("settings put global transition_animation_scale $scale")
            shellManager.sendCommand("settings put global animator_duration_scale $scale")
        }
        val label = when (scale) {
            0.0f -> "OFF"
            0.5f -> "0.5x"
            else -> "1x"
        }
        binding.currentStatus.text = getString(R.string.anim_current, label)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
