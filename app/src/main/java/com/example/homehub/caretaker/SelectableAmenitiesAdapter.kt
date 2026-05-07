package com.example.homehub.caretaker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R

class SelectableAmenitiesAdapter(
    private val amenities: List<String>,
    private val selectedAmenities: MutableList<String>,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<SelectableAmenitiesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkBox: CheckBox = view.findViewById(R.id.amenityCheckBox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_amenity, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val amenity = amenities[position]
        holder.checkBox.text = amenity
        holder.checkBox.isChecked = selectedAmenities.contains(amenity)

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!selectedAmenities.contains(amenity)) selectedAmenities.add(amenity)
            } else {
                selectedAmenities.remove(amenity)
            }
            onSelectionChanged()
        }
    }

    override fun getItemCount(): Int = amenities.size
}
