package com.example.homehub.property

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.Toast
import com.example.homehub.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText

class ReviewDialog : BottomSheetDialogFragment() {
    companion object {
        const val TAG = "ReviewDialog"
    }
    
    var onReviewSubmitted: ((String, Double) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_reviews, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val ratingBar = view.findViewById<RatingBar>(R.id.ratingBar)
        val etReview = view.findViewById<TextInputEditText>(R.id.reviewText)
        val btnSubmit = view.findViewById<View>(R.id.submitButton)
        val btnCancel = view.findViewById<View>(R.id.cancelButton)

        btnSubmit.setOnClickListener {
            val rating = ratingBar.rating.toDouble()
            val text = etReview.text?.toString()?.trim() ?: ""
            
            if (rating == 0.0) {
                Toast.makeText(context, "Please provide a rating", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (text.isEmpty()) {
                Toast.makeText(context, "Please write a review", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            onReviewSubmitted?.invoke(text, rating)
            dismiss()
        }
        
        btnCancel.setOnClickListener {
            dismiss()
        }
    }
}

