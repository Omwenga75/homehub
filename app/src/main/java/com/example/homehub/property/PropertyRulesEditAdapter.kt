package com.example.homehub.property

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R

class PropertyRulesEditAdapter(
    private val rules: MutableList<String>,
    private val onRuleRemoved: (String) -> Unit
) : RecyclerView.Adapter<PropertyRulesEditAdapter.RuleEditViewHolder>() {

    inner class RuleEditViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ruleText: TextView = itemView.findViewById(R.id.ruleText)
        val btnRemove: ImageButton = itemView.findViewById(R.id.btnRemoveRule)

        fun bind(rule: String, position: Int) {
            ruleText.text = "${position + 1}. $rule"
            btnRemove.setOnClickListener {
                rules.removeAt(position)
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, itemCount)
                onRuleRemoved(rule)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleEditViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_property_rule_edit, parent, false)
        return RuleEditViewHolder(view)
    }

    override fun onBindViewHolder(holder: RuleEditViewHolder, position: Int) {
        holder.bind(rules[position], position)
    }

    override fun getItemCount(): Int = rules.size

    fun addRule(rule: String) {
        rules.add(rule)
        notifyItemInserted(rules.size - 1)
    }

    fun getRules(): List<String> = rules.toList()
}
