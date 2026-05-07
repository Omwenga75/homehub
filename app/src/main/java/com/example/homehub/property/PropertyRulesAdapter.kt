package com.example.homehub.property

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R

class PropertyRulesAdapter(
    private val rules: List<String>
) : RecyclerView.Adapter<PropertyRulesAdapter.RuleViewHolder>() {

    inner class RuleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ruleText: TextView = itemView.findViewById(R.id.ruleText)

        fun bind(rule: String, position: Int) {
            // Display rule with number prefix
            ruleText.text = "${position + 1}. $rule"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_property_rule, parent, false)
        return RuleViewHolder(view)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        holder.bind(rules[position], position)
    }

    override fun getItemCount(): Int = rules.size
}
