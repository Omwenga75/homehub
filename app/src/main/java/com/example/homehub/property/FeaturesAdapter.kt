package com.example.homehub.property

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R

class FeaturesAdapter(private val features: List<Feature>) : RecyclerView.Adapter<FeaturesAdapter.FeatureViewHolder>() {

    inner class FeatureViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.featureIcon)
        val name: TextView = itemView.findViewById(R.id.featureName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeatureViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_feature, parent, false)
        return FeatureViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeatureViewHolder, position: Int) {
        val feature = features[position]
        holder.name.text = feature.name
        holder.icon.setImageResource(feature.iconRes)
    }

    override fun getItemCount(): Int = features.size
}
