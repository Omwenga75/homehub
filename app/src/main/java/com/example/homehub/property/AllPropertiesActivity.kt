package com.example.homehub.property

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.homehub.property.Property
import com.example.homehub.utils.InteractionManager
import com.example.homehub.property.PropertyDataHolder
import com.example.homehub.R
import com.example.homehub.property.UnifiedPropertyAdapter
import com.example.homehub.databinding.ActivityAllPropertiesBinding
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class AllPropertiesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllPropertiesBinding
    private lateinit var propertyAdapter: UnifiedPropertyAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var isManagementMode = false
    private var currentCategory = "All"

    companion object {
        const val EXTRA_MODE = "EXTRA_MODE"
        const val MODE_DISCOVERY = "DISCOVERY"
        const val MODE_MY_PROPERTIES = "MY_PROPERTIES"
        const val EXTRA_SECTION_TITLE = "SECTION_TITLE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllPropertiesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isManagementMode = intent.getStringExtra(EXTRA_MODE) == MODE_MY_PROPERTIES
        
        setupUI()
        loadData()
    }

    private fun setupUI() {
        val title = intent.getStringExtra(EXTRA_SECTION_TITLE) ?: 
                    if (isManagementMode) "My Properties" else "All Properties"
        
        binding.tvTitle.text = title
        binding.btnBack.setOnClickListener { finish() }

        binding.addPropertyButton.apply {
            visibility = if (isManagementMode) View.VISIBLE else View.GONE
            setOnClickListener { openAddProperty() }
        }

        val layoutRes = if (isManagementMode) R.layout.item_property else R.layout.item_house_grid
        
        propertyAdapter = UnifiedPropertyAdapter(
            layoutResId = layoutRes,
            onItemClick = { property, _ -> openPropertyDetails(property) },
            onFavoriteClick = { property, _ -> toggleFavorite(property) },
            onMoreClick = { property, view -> if (isManagementMode) showManagementOptions(property, view) }
        )

        binding.rvProperties.apply {
            layoutManager = if (isManagementMode) {
                LinearLayoutManager(this@AllPropertiesActivity)
            } else {
                GridLayoutManager(this@AllPropertiesActivity, 2)
            }
            adapter = propertyAdapter
        }

        if (!isManagementMode) {
            setupCategoryChips(title)
        } else {
            binding.chipGroupCategories.visibility = View.GONE
        }
    }

    private fun setupCategoryChips(initialTitle: String) {
        val categories = listOf("All", "Single Rooms", "Bedsitters", "1 Bedroom", "2 Bedroom")
        binding.chipGroupCategories.removeAllViews()

        for (category in categories) {
            val chip = Chip(this).apply {
                text = category
                isCheckable = true
                isChecked = category.equals(initialTitle, ignoreCase = true) || 
                            (category == "All" && initialTitle == "All Properties")
                
                // Premium Styling
                setChipBackgroundColorResource(if (isChecked) R.color.white else R.color.primary_dark)
                setTextColor(if (isChecked) getColor(R.color.primary_dark) else getColor(R.color.true_white))
                setChipStrokeColorResource(R.color.white_translucent)
                setChipStrokeWidth(1f)
                
                setOnClickListener {
                    currentCategory = category
                    loadFilteredProperties(category)
                    // Refresh chips styling
                    setupCategoryChips(category)
                }
            }
            binding.chipGroupCategories.addView(chip)
        }
    }

    private fun runLayoutAnimation() {
        val recyclerView = binding.rvProperties
        val context = recyclerView.context
        val controller = android.view.animation.AnimationUtils.loadLayoutAnimation(context, R.anim.layout_animation_fall_down)
        recyclerView.layoutAnimation = controller
        recyclerView.adapter?.notifyDataSetChanged()
        recyclerView.scheduleLayoutAnimation()
    }

    private fun loadData() {
        if (isManagementMode) {
            loadCaretakerProperties()
        } else {
            val initialTitle = intent.getStringExtra(EXTRA_SECTION_TITLE) ?: "All"
            val cachedList = PropertyDataHolder.getPropertyList()
            if (cachedList != null) {
                updateUI(cachedList)
                PropertyDataHolder.clear()
            } else {
                loadFilteredProperties(initialTitle)
            }
        }
    }

    private fun loadCaretakerProperties() {
        val userId = auth.currentUser?.uid ?: return
        binding.progressBar.visibility = View.VISIBLE
        
        db.collection("properties")
            .whereEqualTo("caretakerId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                binding.progressBar.visibility = View.GONE
                if (e != null) {
                    Log.e("AllProperties", "Error fetching properties", e)
                    return@addSnapshotListener
                }

                val properties = snapshot?.documents?.mapNotNull { 
                    Property.fromDocument(it.data ?: emptyMap()).apply { id = it.id } 
                }?.filter { !it.isDeleted } ?: emptyList()

                updateUI(properties)
            }
    }

    private fun loadFilteredProperties(category: String) {
        binding.progressBar.visibility = View.VISIBLE
        
        var query = db.collection("properties")
            .whereEqualTo("available", true)
        
        if (category != "All" && category != "All Properties") {
            val normalizedCategory = when (category) {
                "Single Rooms" -> "Single Room"
                "Bedsitters" -> "Bedsitter"
                else -> category
            }
            query = query.whereEqualTo("category", normalizedCategory)
        }

        query.get().addOnSuccessListener { docs ->
            binding.progressBar.visibility = View.GONE
            val properties = docs.mapNotNull { 
                Property.fromDocument(it.data).apply { id = it.id } 
            }.filter { it.isAvailable() }
            
            updateUI(properties)
        }.addOnFailureListener {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "Failed to load properties", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI(properties: List<Property>) {
        binding.propertiesCount.text = "${properties.size} listings found"
        if (properties.isEmpty()) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            propertyAdapter.submitList(emptyList())
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            propertyAdapter.submitList(properties)
            runLayoutAnimation()
        }
    }

    private fun toggleFavorite(property: Property) {
        val targetState = !property.isFavorite
        InteractionManager.toggleLike(property.id, targetState) { success ->
            if (success) {
                property.isFavorite = targetState
                runOnUiThread {
                    Toast.makeText(this, if (targetState) "Property liked!" else "Removed from likes", Toast.LENGTH_SHORT).show()
                    propertyAdapter.notifyDataSetChanged()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Failed to update like", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showManagementOptions(property: Property, anchor: View) {
        // Implementation logic
    }

    private fun openPropertyDetails(property: Property) {
        PropertyDataHolder.selectedProperty = property
        val intent = Intent(this, PropertyDetailsActivity::class.java).apply {
            putExtra("PROPERTY_ID", property.id)
            putExtra("EXTRA_TITLE", property.displayTitle)
            putExtra("EXTRA_LOCATION", property.location)
            putExtra("EXTRA_PRICE", property.getFormattedPrice())
            // Object passed via holder instead of intent
        }
        startActivity(intent)
    }

    private fun openAddProperty() {
        startActivity(Intent(this, AddPropertyActivity::class.java))
    }
}
