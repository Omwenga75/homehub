package com.example.homehub.caretaker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.homehub.databinding.BottomSheetPerformanceBreakdownBinding
import com.example.homehub.property.Property
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PerformanceBreakdownBottomSheet(private val properties: List<Property>) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPerformanceBreakdownBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPerformanceBreakdownBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvPerformance.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPerformance.adapter = PerformanceAdapter(properties)

        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnDone.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PerformanceBreakdown"
    }
}
