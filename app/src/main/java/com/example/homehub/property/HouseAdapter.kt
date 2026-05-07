package com.example.homehub.property

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.databinding.ActivityFilterV8Binding

class HouseAdapter(private val properties: List<Property>) : RecyclerView.Adapter<HouseAdapter.ViewHolder>() {
    class ViewHolder(val binding: ActivityFilterV8Binding) : RecyclerView.ViewHolder(binding.root) // simplified
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = TODO()
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = TODO()
    override fun getItemCount(): Int = properties.size
}
