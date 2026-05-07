package com.example.homehub.property

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.homehub.R
import com.example.homehub.databinding.ActivityFilterV8Binding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class FilterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFilterV8Binding
    private var filterData = FilterData()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFilterV8Binding.inflate(layoutInflater)
        setContentView(binding.root)

        val passedData = intent.getParcelableExtra<FilterData>("filter_data")
        if (passedData != null) {
            filterData = passedData
        }

        setupChipGroup(binding.idV8ChipGroupBedrooms)
        setupChipGroup(binding.idV8ChipGroupAmenities)
        
        prefillExistingFilters()

        binding.idV8PriceSlider.addOnChangeListener { slider, _, _ ->
            val vals = slider.values
            if (vals.size == 2) {
                binding.idV8TvMinPrice.text = "KSh " + String.format("%,.0f", vals[0])
                binding.idV8TvMaxPrice.text = "KSh " + String.format("%,.0f", vals[1])
            }
        }

        binding.idV8BtnApply.setOnClickListener {
            applyAndReturn()
        }
    }

    private fun prefillExistingFilters() {
        // Price Slider
        try {
            val minSlider = binding.idV8PriceSlider.valueFrom
            val maxSlider = binding.idV8PriceSlider.valueTo
            
            val initialMin = filterData.minPrice.toFloat().coerceIn(minSlider, maxSlider)
            val initialMax = filterData.maxPrice.toFloat().coerceIn(minSlider, maxSlider)
            
            val finalMin = minOf(initialMin, initialMax)
            val finalMax = maxOf(initialMin, initialMax)
            
            binding.idV8PriceSlider.values = listOf(finalMin, finalMax)
        } catch(e: Exception) {
            binding.idV8PriceSlider.values = listOf(binding.idV8PriceSlider.valueFrom, binding.idV8PriceSlider.valueTo)
        }

        val vals = binding.idV8PriceSlider.values
        if (vals.size == 2) {
            binding.idV8TvMinPrice.text = "KSh " + String.format("%,.0f", vals[0])
            binding.idV8TvMaxPrice.text = "KSh " + String.format("%,.0f", vals[1])
        }

        // Amenities
        for (i in 0 until binding.idV8ChipGroupAmenities.childCount) {
            val chip = binding.idV8ChipGroupAmenities.getChildAt(i) as? Chip ?: continue
            if (filterData.amenities.contains(chip.text.toString())) {
                chip.isChecked = true
            }
        }

        // Bedrooms
        for (i in 0 until binding.idV8ChipGroupBedrooms.childCount) {
            val chip = binding.idV8ChipGroupBedrooms.getChildAt(i) as? Chip ?: continue
            val countStr = chip.tag?.toString() ?: ""
            if (countStr.isNotEmpty()) {
                val bedVal = countStr.toIntOrNull()
                if (bedVal != null && filterData.bedroomCounts.contains(bedVal)) {
                    chip.isChecked = true
                }
            } else if (chip.text.toString().equals("Any", ignoreCase = true) && filterData.bedroomCounts.isEmpty()) {
                chip.isChecked = true
            }
        }

    }

    private fun applyAndReturn() {
        val vals = binding.idV8PriceSlider.values
        if (vals.size == 2) {
            filterData.minPrice = vals[0].toDouble()
            filterData.maxPrice = vals[1].toDouble()
        }

        val selectedAmenities = mutableSetOf<String>()
        for (i in 0 until binding.idV8ChipGroupAmenities.childCount) {
            val chip = binding.idV8ChipGroupAmenities.getChildAt(i) as? Chip ?: continue
            if (chip.isChecked) {
                selectedAmenities.add(chip.text.toString())
            }
        }
        filterData.amenities = selectedAmenities

        val selectedBedrooms = mutableSetOf<Int>()
        var anyBedroomsSelected = false
        for (i in 0 until binding.idV8ChipGroupBedrooms.childCount) {
            val chip = binding.idV8ChipGroupBedrooms.getChildAt(i) as? Chip ?: continue
            if (chip.isChecked) {
                if (chip.text.toString().equals("Any", ignoreCase = true)) {
                    anyBedroomsSelected = true
                    break
                }
                val valStr = chip.tag?.toString()
                if (valStr != null) {
                    valStr.toIntOrNull()?.let { selectedBedrooms.add(it) }
                }
            }
        }
        filterData.bedroomCounts = if (anyBedroomsSelected) emptySet() else selectedBedrooms

        val resultIntent = Intent()
        resultIntent.putExtra("filter_data", filterData)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun setupChipGroup(chipGroup: ChipGroup) {
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as? Chip ?: continue
            
            chip.isCheckedIconVisible = false
            chip.isCheckable = true
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            chip.setChipBackgroundColorResource(R.color.surface)
            chip.setChipStrokeColorResource(R.color.border_light)
            chip.chipStrokeWidth = 1f
            
            // Re-apply style if it was pre-checked
            if (chip.isChecked) {
                chip.setChipBackgroundColorResource(R.color.primary_dark)
                chip.setTextColor(ContextCompat.getColor(this, R.color.white))
            }

            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    chip.setChipBackgroundColorResource(R.color.primary_dark)
                    chip.setTextColor(ContextCompat.getColor(this, R.color.white))
                } else {
                    chip.setChipBackgroundColorResource(R.color.surface)
                    chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                }
            }
        }
    }
}
