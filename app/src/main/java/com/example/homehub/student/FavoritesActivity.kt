package com.example.homehub.student

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

import com.example.homehub.R
import com.example.homehub.databinding.ActivityFavoritesBinding
import com.example.homehub.property.UnifiedPropertyAdapter
import com.example.homehub.property.Property
import com.example.homehub.utils.InteractionManager
import com.example.homehub.property.PropertyDetailsActivity

class FavoritesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoritesBinding
    private lateinit var propertyAdapter: UnifiedPropertyAdapter
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadFavorites()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        propertyAdapter = UnifiedPropertyAdapter(
            layoutResId = R.layout.item_house_vertical, // Use full width cards for favorites
            onItemClick = { property, _ ->
                com.example.homehub.property.PropertyDataHolder.selectedProperty = property
                val intent = Intent(this, PropertyDetailsActivity::class.java).apply {
                    putExtra("PROPERTY_ID", property.id)
                    putExtra("EXTRA_TITLE", property.displayTitle)
                    putExtra("EXTRA_LOCATION", property.location)
                    putExtra("EXTRA_PRICE", property.getFormattedPrice())
                }
                startActivity(intent)
            },
            onFavoriteClick = { property, _ ->
                // Toggle favorite
                toggleFavorite(property)
            }
        )

        binding.rvFavorites.apply {
            layoutManager = LinearLayoutManager(this@FavoritesActivity)
            adapter = propertyAdapter
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadFavorites()
        }
    }

    private fun toggleFavorite(property: Property) {
        // Toggle from true (current) to false (target)
        InteractionManager.toggleLike(property.id, false) { success ->
            if (success) {
                runOnUiThread { loadFavorites() } // Reload list
            }
        }
    }

    private fun loadFavorites() {
        val user = auth.currentUser
        if (user == null) {
            binding.swipeRefresh.isRefreshing = false
            showEmptyState()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        db.collection("users").document(user.uid).collection("favorites")
            .get()
            .addOnSuccessListener { snapshot ->
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                
                if (snapshot.isEmpty) {
                    showEmptyState()
                } else {
                    val favoriteIds = snapshot.documents.map { it.id }
                    fetchPropertyDetails(favoriteIds)
                }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                showEmptyState()
            }
    }

    private fun fetchPropertyDetails(ids: List<String>) {
        if (ids.isEmpty()) {
            showEmptyState()
            return
        }

        // Fetch properties from "properties" collection efficiently using whereIn
        // Note: whereIn is limited to 10-30 items depending on Firestore version, 
        // but for favorites a take(10) or chunked approach is safer if the user has many.
        val targetIds = ids.take(10) 

        db.collection("properties")
            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), targetIds)
            .get()
            .addOnSuccessListener { snapshot ->
                val favoriteProperties = snapshot.documents.mapNotNull { doc ->
                    try {
                        val property = Property.fromDocument(doc.data ?: emptyMap())
                        property.let { p ->
                            p.id = doc.id
                            p.isFavorite = true // They are favorites
                            p
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                
                if (favoriteProperties.isEmpty()) {
                    showEmptyState()
                } else {
                    hideEmptyState()
                    propertyAdapter.submitList(favoriteProperties)
                }
            }
            .addOnFailureListener {
                showEmptyState()
            }
    }

    private fun showEmptyState() {
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.rvFavorites.visibility = View.GONE
        propertyAdapter.submitList(emptyList())
    }

    private fun hideEmptyState() {
        binding.emptyStateLayout.visibility = View.GONE
        binding.rvFavorites.visibility = View.VISIBLE
    }
}
