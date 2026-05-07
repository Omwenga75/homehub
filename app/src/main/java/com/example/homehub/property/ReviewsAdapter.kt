package com.example.homehub.property

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R

class ReviewsAdapter(private val reviews: List<Review>) : RecyclerView.Adapter<ReviewsAdapter.ReviewViewHolder>() {

    inner class ReviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val leftLayout: LinearLayout = itemView.findViewById(R.id.leftReviewLayout)
        val rightLayout: LinearLayout = itemView.findViewById(R.id.rightReviewLayout)

        // Left Views
        val leftImage: ImageView = itemView.findViewById(R.id.reviewerImageLeft)
        val leftName: TextView = itemView.findViewById(R.id.reviewerNameLeft)
        val leftDate: TextView = itemView.findViewById(R.id.reviewDateLeft)
        val leftText: TextView = itemView.findViewById(R.id.reviewTextLeft)
        val leftRatingText: TextView = itemView.findViewById(R.id.ratingTextLeft)

        // Right Views
        val rightImage: ImageView = itemView.findViewById(R.id.reviewerImageRight)
        val rightName: TextView = itemView.findViewById(R.id.reviewerNameRight)
        val rightDate: TextView = itemView.findViewById(R.id.reviewDateRight)
        val rightText: TextView = itemView.findViewById(R.id.reviewTextRight)
        val rightRatingText: TextView = itemView.findViewById(R.id.ratingTextRight)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_review, parent, false)
        return ReviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = reviews[position]

        if (position % 2 == 0) {
            holder.leftLayout.visibility = View.VISIBLE
            holder.rightLayout.visibility = View.GONE

            holder.leftName.text = review.reviewerName
            holder.leftText.text = review.reviewText
            holder.leftDate.text = "- ${review.date}"
            holder.leftRatingText.text = String.format("%.1f", review.rating)
            if (review.reviewerImage != 0) {
                holder.leftImage.setImageResource(review.reviewerImage)
            }
        } else {
            holder.rightLayout.visibility = View.VISIBLE
            holder.leftLayout.visibility = View.GONE

            holder.rightName.text = review.reviewerName
            holder.rightText.text = review.reviewText
            holder.rightDate.text = "- ${review.date}"
            holder.rightRatingText.text = String.format("%.1f", review.rating)
            if (review.reviewerImage != 0) {
                holder.rightImage.setImageResource(review.reviewerImage)
            }
        }
    }

    override fun getItemCount(): Int = reviews.size
}
