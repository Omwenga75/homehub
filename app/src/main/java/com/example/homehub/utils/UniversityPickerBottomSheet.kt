package com.example.homehub.utils

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class UniversityPickerBottomSheet(
    private val onSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "UniversityPicker"
    }

    private lateinit var adapter: UniAdapter
    private var fullList = KenyanUniversities.allInstitutions

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.bottom_sheet_university_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvUniversities)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val tvCount = view.findViewById<TextView>(R.id.tvUniCount)
        val emptyState = view.findViewById<LinearLayout>(R.id.emptyState)

        adapter = UniAdapter(fullList.toMutableList()) { selected ->
            onSelected(selected)
            dismiss()
        }

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        tvCount.text = "${fullList.size} institutions available"

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                val filtered = KenyanUniversities.search(query)
                adapter.updateList(filtered)
                tvCount.text = "${filtered.size} institutions found"

                if (filtered.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                    rv.visibility = View.GONE
                } else {
                    emptyState.visibility = View.GONE
                    rv.visibility = View.VISIBLE
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            it.layoutParams.height = (resources.displayMetrics.heightPixels * 0.8).toInt()
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.8).toInt()
        }
    }

    // ──────── Inner Adapter ────────
    private class UniAdapter(
        private val items: MutableList<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<UniAdapter.VH>() {

        fun updateList(newList: List<String>) {
            items.clear()
            items.addAll(newList)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_university, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val name = items[position]
            holder.tvName.text = name
            holder.itemView.setOnClickListener { onClick(name) }
        }

        override fun getItemCount(): Int = items.size

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvUniName)
        }
    }
}
