package com.example.homehub.property

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.homehub.R
import com.example.homehub.property.Review
import com.example.homehub.property.ReviewDialog
import com.example.homehub.property.ReviewsAdapter
import com.example.homehub.databinding.ActivityReviewsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class ReviewsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var reviewsAdapter: ReviewsAdapter
    private val reviewsList = mutableListOf<Review>()
    
    private var propertyId: String? = null
    private var caretakerId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)
        db = FirebaseFirestore.getInstance()

        propertyId = intent.getStringExtra("PROPERTY_ID")
        caretakerId = intent.getStringExtra("CARETAKER_ID")

        setupRecyclerView()
        setupListeners()
        loadReviews()
    }

    private fun setupRecyclerView() {
        reviewsAdapter = ReviewsAdapter(reviewsList)
        binding.rvReviews.layoutManager = LinearLayoutManager(this)
        binding.rvReviews.adapter = reviewsAdapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnWriteReview.setOnClickListener {
            showReviewDialog()
        }
    }

    private fun showReviewDialog() {
        val dialog = ReviewDialog()
        dialog.onReviewSubmitted = { text, rating ->
            submitReview(text, rating.toDouble())
        }
        dialog.show(supportFragmentManager, ReviewDialog.TAG)
    }

    private fun loadReviews() {
        binding.progressBar.visibility = View.VISIBLE
        
        var query: Query = db.collection("reviews")
        
        if (!propertyId.isNullOrEmpty()) {
            query = query.whereEqualTo("propertyId", propertyId)
        } else if (!caretakerId.isNullOrEmpty()) {
            query = query.whereEqualTo("caretakerId", caretakerId)
        }
        
        query.orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                binding.progressBar.visibility = View.GONE
                
                if (e != null) {
                    Log.w("ReviewsActivity", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    reviewsList.clear()
                    for (doc in snapshot.documents) {
                        val review = Review.fromDocument(doc.data ?: emptyMap())
                        reviewsList.add(review)
                    }
                    
                    updateUI()
                }
            }
    }

    private fun updateUI() {
        reviewsAdapter.notifyDataSetChanged()
        
        if (reviewsList.isEmpty()) {
            binding.tvAverageRating.text = "0.0"
            binding.tvTotalReviews.text = "No reviews yet"
            binding.ratingIndicator.rating = 0f
            updateDistributionBars(emptyList())
            return
        }

        val totalReviews = reviewsList.size
        val avgRating = reviewsList.map { it.rating }.average()
        
        binding.tvAverageRating.text = String.format("%.1f", avgRating)
        binding.tvTotalReviews.text = "$totalReviews ${if (totalReviews == 1) "Review" else "Reviews"}"
        binding.ratingIndicator.rating = avgRating.toFloat()
        
        updateDistributionBars(reviewsList)
    }

    private fun updateDistributionBars(reviews: List<Review>) {
        val total = reviews.size.toDouble()
        if (total == 0.0) {
            resetBars()
            return
        }

        val counts = IntArray(6)
        reviews.forEach { 
            val r = it.rating.toInt()
            if (r in 1..5) counts[r]++
        }

        binding.row5.ratingProgress.progress = ((counts[5] / total) * 100).toInt()
        binding.row4.ratingProgress.progress = ((counts[4] / total) * 100).toInt()
        binding.row3.ratingProgress.progress = ((counts[3] / total) * 100).toInt()
        binding.row2.ratingProgress.progress = ((counts[2] / total) * 100).toInt()
        binding.row1.ratingProgress.progress = ((counts[1] / total) * 100).toInt()
        
        binding.row5.tvRatingNum.text = "5"
        binding.row4.tvRatingNum.text = "4"
        binding.row3.tvRatingNum.text = "3"
        binding.row2.tvRatingNum.text = "2"
        binding.row1.tvRatingNum.text = "1"
    }

    private fun resetBars() {
        binding.row5.ratingProgress.progress = 0
        binding.row4.ratingProgress.progress = 0
        binding.row3.ratingProgress.progress = 0
        binding.row2.ratingProgress.progress = 0
        binding.row1.ratingProgress.progress = 0
    }

    private fun submitReview(text: String, rating: Double) {
        val user = FirebaseAuth.getInstance().currentUser
        val reviewerName = user?.displayName ?: "Anonymous Guest"
        val reviewerId = user?.uid ?: ""
        
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = sdf.format(Date())

        val review = Review(
            reviewerName = reviewerName,
            reviewText = text,
            date = currentDate,
            reviewerImage = R.drawable.ic_profile,
            rating = rating,
            reviewerId = reviewerId,
            propertyId = propertyId ?: "",
            caretakerId = caretakerId ?: "",
            timestamp = System.currentTimeMillis()
        )

        db.collection("reviews").add(review)
            .addOnSuccessListener {
                Toast.makeText(this, "Review submitted!", Toast.LENGTH_SHORT).show()
                loadReviews()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to submit review", Toast.LENGTH_SHORT).show()
            }
    }
}
