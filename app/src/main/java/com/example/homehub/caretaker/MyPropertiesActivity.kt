package com.example.homehub.caretaker

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.example.homehub.property.Property
import com.example.homehub.property.UnifiedPropertyAdapter
import com.example.homehub.utils.HybridPropertyManager
import com.example.homehub.property.PropertyDetailsActivity
import com.example.homehub.property.AddPropertyActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.*

class MyPropertiesActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var propertiesRecyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var propertiesAdapter: UnifiedPropertyAdapter
    private val propertiesList = ArrayList<Property>()

    private lateinit var localBroadcastManager: LocalBroadcastManager
    private lateinit var progressBar: ProgressBar

    // Real-time listener
    private var propertiesListener: ListenerRegistration? = null

    // Broadcast receiver for admin updates
    private val propertyUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val propertyId = intent?.getStringExtra("propertyId")
            val action = intent?.getStringExtra("action")

            Log.d("MyProperties", "📢 Received update for property: $propertyId, action: $action")

            when (action) {
                "suspended", "activated", "soft_deleted", "restored" -> {
                    // Refresh the specific property or reload all
                    refreshProperty(propertyId)
                }
                "permanently_deleted" -> {
                    // Remove from list
                    removePropertyFromList(propertyId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_properties)

        // Standard Emerald Green status bar for consistency
        window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.primary_dark)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        localBroadcastManager = LocalBroadcastManager.getInstance(this)

        initializeViews()
        setupRecyclerView()

        // Register broadcast receiver
        registerBroadcastReceiver()

        // Load local properties immediately (FAST)
        loadLocalPropertiesFirst()

        // Then setup real-time Firebase listener
        setupRealTimeListener()
    }

    private fun registerBroadcastReceiver() {
        val filter = IntentFilter().apply {
            addAction("PROPERTY_STATUS_UPDATED")
            addAction("PROPERTY_DELETED")
        }
        localBroadcastManager.registerReceiver(propertyUpdateReceiver, filter)
    }

    private fun refreshProperty(propertyId: String?) {
        if (propertyId == null) return

        // Refresh from Firebase for accurate data
        db.collection("properties").document(propertyId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val propertyData = document.data ?: return@addOnSuccessListener
                    val updatedProperty = Property.fromDocument(propertyData)
                    updatedProperty.id = document.id

                    // Update in local list
                    updatePropertyInList(updatedProperty)
                } else {
                    // Property doesn't exist (might be deleted)
                    removePropertyFromList(propertyId)
                }
            }
            .addOnFailureListener { e ->
                Log.e("MyProperties", "❌ Error refreshing property: ${e.message}")
            }
    }

    private fun updatePropertyInList(property: Property) {
        val index = propertiesList.indexOfFirst { it.id == property.id }
        if (index != -1) {
            propertiesList[index] = property
            propertiesAdapter.notifyItemChanged(index)
            Log.d("MyProperties", "🔄 Updated property in list: ${property.title}")
        } else {
            // Property not in list, check if it belongs to current caretaker
            val currentUser = auth.currentUser
            if (currentUser != null && property.caretakerId == currentUser.uid) {
                propertiesList.add(property)
                propertiesAdapter.notifyItemInserted(propertiesList.size - 1)
                Log.d("MyProperties", "➕ Added property to list: ${property.title}")
            }
        }
        updateEmptyState()
    }

    private fun removePropertyFromList(propertyId: String?) {
        if (propertyId == null) return

        val index = propertiesList.indexOfFirst { it.id == propertyId }
        if (index != -1) {
            propertiesList.removeAt(index)
            propertiesAdapter.notifyItemRemoved(index)
            Log.d("MyProperties", "➖ Removed property from list: $propertyId")
            updateEmptyState()
        }
    }

    private fun initializeViews() {
        // Setup Toolbar - Elite Suite Style
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "" // Centered TextView handles the title

        // Back button
        findViewById<View>(R.id.backButton).setOnClickListener {
            onBackPressed()
        }

        // Add Property FAB (Restored)
        findViewById<View>(R.id.addPropertyFAB).setOnClickListener {
            openAddProperty()
        }

        // Initialize other views
        emptyState = findViewById(R.id.emptyState)
        progressBar = findViewById(R.id.progressBar)
        propertiesRecyclerView = findViewById(R.id.propertiesRecyclerView)
    }

    private fun setupRecyclerView() {
        propertiesAdapter = UnifiedPropertyAdapter(
            layoutResId = R.layout.item_my_property,
            onItemClick = { property, _ ->
                openPropertyDetails(property)
            },
            onFavoriteClick = null,
            onMoreClick = { property, view ->
                showMoreOptions(property, view)
            }
        )

        propertiesRecyclerView.layoutManager = LinearLayoutManager(this)
        propertiesRecyclerView.adapter = propertiesAdapter
        propertiesAdapter.submitList(propertiesList)
    }

    // NEW: Load local properties immediately
    @SuppressLint("NotifyDataSetChanged")
    private fun loadLocalPropertiesFirst() {
        val currentUser = auth.currentUser ?: return

        val localProperties = HybridPropertyManager.getLocalProperties(this)

        if (localProperties.isNotEmpty()) {
            propertiesList.clear()

            localProperties.forEach { propertyData ->
                try {
                    val property = Property.fromDocument(propertyData)
                    // Only add properties that belong to the current caretaker
                    if (property.caretakerId == currentUser.uid) {
                        propertiesList.add(property)
                        Log.d("MyProperties", "📱 Loaded local property: ${property.title} - Status: ${property.status}")
                    }
                } catch (e: Exception) {
                    Log.e("MyProperties", "❌ Error converting local property: ${e.message}")
                }
            }

            propertiesAdapter.submitList(ArrayList(propertiesList))
            updateEmptyState()
            Log.d("MyProperties", "✅ Loaded ${propertiesList.size} local properties for current caretaker")
        }
    }


    @SuppressLint("NotifyDataSetChanged")
    private fun setupRealTimeListener() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            progressBar.visibility = View.VISIBLE

            // Listen to properties for current caretaker only
            propertiesListener = db.collection("properties")
                .whereEqualTo("caretakerId", currentUser.uid)
                .addSnapshotListener { snapshot, error ->
                    progressBar.visibility = View.GONE

                    if (error != null) {
                        Log.e("MyProperties", "❌ Error listening to properties: ${error.message}")
                        showError("Failed to load properties")
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        // Create a map of existing properties for quick lookup
                        val existingPropertyMap = propertiesList.associateBy { it.id }

                        // Process each document
                        for (document in snapshot.documents) {
                            try {
                                val propertyData = document.data ?: continue
                                val property = Property.fromDocument(propertyData)
                                property.id = document.id

                                // Skip deleted properties
                                if (property.isDeleted) {
                                    removePropertyFromList(property.id)
                                    continue
                                }

                                // Check if property already exists in list
                                val existingProperty = existingPropertyMap[property.id]
                                if (existingProperty != null) {
                                    // Update existing property
                                    val index = propertiesList.indexOfFirst { it.id == property.id }
                                    if (index != -1) {
                                        propertiesList[index] = property
                                        propertiesAdapter.notifyItemChanged(index)
                                    }
                                } else {
                                    // Add new property
                                    propertiesList.add(property)
                                }
                            } catch (e: Exception) {
                                Log.e("MyProperties", "❌ Error parsing property: ${e.message}")
                            }
                        }

                        // Remove properties that no longer exist in Firebase
                        val firebaseIds = snapshot.documents.map { it.id }.toSet()
                        propertiesList.removeAll { !firebaseIds.contains(it.id) && !it.id.startsWith("local_") }

                        // Sort by creation date (newest first)
                        propertiesList.sortByDescending { it.createdAt }

                        propertiesAdapter.submitList(ArrayList(propertiesList))
                        updateEmptyState()

                        Log.d("MyProperties", "✅ Real-time update: ${propertiesList.size} properties for caretaker ${currentUser.uid}")

                    } else {
                        // No Firebase properties, but we might have local ones
                        updateEmptyState()
                        Log.d("MyProperties", "ℹ️ No Firebase properties, showing local only")
                    }
                }
        } else {
            progressBar.visibility = View.GONE
            showError("Please log in to view your properties")
        }
    }

    private fun updateEmptyState() {
        val hasProperties = propertiesList.isNotEmpty()

        // ALWAYS show properties list if we have properties (both Active and Inactive)
        if (hasProperties) {
            propertiesRecyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
        } else {
            // Only show empty state when there are NO properties at all
            propertiesRecyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE

            // Set up empty state action
            emptyState.findViewById<MaterialButton>(R.id.emptyStateAction).setOnClickListener {
                openAddProperty()
            }

            // Update empty state messages
            emptyState.findViewById<TextView>(R.id.emptyStateTitle).text = "No Properties Found"
            emptyState.findViewById<TextView>(R.id.emptyStateMessage).text = "You haven't added any properties yet"
            emptyState.findViewById<MaterialButton>(R.id.emptyStateAction).text = "Add Your First Property"
        }

        Log.d("MyProperties", "📊 Empty state updated: ${propertiesList.size} properties, ${propertiesList.count { it.status == "Active" }} active")
    }

    private fun openPropertyDetails(property: Property) {
        com.example.homehub.property.PropertyDataHolder.selectedProperty = property
        val intent = Intent(this, PropertyDetailsActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun openPropertyBookings(property: Property) {
        val intent = Intent(this, CaretakerBookingsActivity::class.java)
        intent.putExtra("PROPERTY_ID", property.id)
        intent.putExtra("PROPERTY_NAME", property.title)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun editProperty(property: Property) {
        // Only pass property ID — avoid TransactionTooLargeException from large image lists
        val intent = Intent(this, AddPropertyActivity::class.java)
        intent.putExtra("EDIT_PROPERTY_ID", property.id)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun openAddProperty() {
        val intent = Intent(this, AddPropertyActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun showMoreOptions(property: Property, anchorView: View) {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.property_options_menu, popup.menu)

        // Check if property is suspended by admin
        val isSuspendedByAdmin = property.isSuspendedByAdmin()

        // Edit and View options
        val editItem = popup.menu.findItem(R.id.menu_edit)
        editItem?.isVisible = !isSuspendedByAdmin

        // Show/hide delete option
        val deleteItem = popup.menu.findItem(R.id.menu_delete)
        deleteItem?.isVisible = !isSuspendedByAdmin

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_view_details -> {
                    openPropertyDetails(property)
                    true
                }
                R.id.menu_view_bookings -> {
                    openPropertyBookings(property)
                    true
                }
                R.id.menu_manage_units -> {
                    managePropertyUnits(property)
                    true
                }
                R.id.menu_edit -> {
                    if (!isSuspendedByAdmin) {
                        editProperty(property)
                        true
                    } else {
                        Toast.makeText(this, "Cannot edit suspended property", Toast.LENGTH_SHORT).show()
                        false
                    }
                }
                R.id.menu_delete -> {
                    if (!isSuspendedByAdmin) {
                        deleteProperty(property)
                        true
                    } else {
                        Toast.makeText(this, "Cannot delete suspended property", Toast.LENGTH_SHORT).show()
                        false
                    }
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun managePropertyUnits(property: Property) {
        val intent = Intent(this, RoomManagerActivity::class.java)
        intent.putExtra("PROPERTY_ID", property.id)
        intent.putExtra("PROPERTY_TITLE", property.title)
        startActivity(intent)
    }

    private fun deleteProperty(property: Property) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Property")
            .setMessage("Are you sure you want to permanently delete '${property.title}'? This action cannot be undone and the property will be removed from both your properties and user dashboard.")
            .setPositiveButton("Delete") { dialog, which ->
                performDeleteProperty(property)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performDeleteProperty(property: Property) {
        progressBar.visibility = View.VISIBLE

        val propertyIdToDelete = property.id
        val propertyTitle = property.title

        Log.d("MyProperties", "🗑️ STARTING DELETION: $propertyIdToDelete - '$propertyTitle'")

        // 1. Remove from UI list immediately
        val initialListSize = propertiesList.size
        propertiesList.removeAll { it.id == propertyIdToDelete }
        val removedFromList = initialListSize - propertiesList.size

        Log.d("MyProperties", "📋 UI List - Before: $initialListSize, After: ${propertiesList.size}, Removed: $removedFromList")

        // 2. Update UI immediately
        propertiesAdapter.submitList(ArrayList(propertiesList))
        updateEmptyState()

        // 3. Delete from local storage
        Log.d("MyProperties", "📱 Deleting from local storage...")
        HybridPropertyManager.deleteLocalProperty(this, propertyIdToDelete)

        // 4. Delete from Firebase (if it has a Firebase ID)
        if (propertyIdToDelete.isNotEmpty() && !propertyIdToDelete.startsWith("local_")) {
            Log.d("MyProperties", "🔥 Deleting from Firebase...")

            db.collection("properties").document(propertyIdToDelete)
                .delete()
                .addOnSuccessListener {
                    progressBar.visibility = View.GONE
                    Log.d("MyProperties", "✅ SUCCESS: Deleted from Firebase - $propertyIdToDelete")
                    showSuccess("'$propertyTitle' deleted successfully")

                    // Final cleanup check
                    finalCleanupCheck(propertyIdToDelete)
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    Log.e("MyProperties", "❌ FAILED Firebase deletion: ${e.message}")

                    // Still show success since local deletion worked
                    showSuccess("'$propertyTitle' deleted locally")

                    // Final cleanup check
                    finalCleanupCheck(propertyIdToDelete)
                }
        } else {
            // Local-only property
            progressBar.visibility = View.GONE
            Log.d("MyProperties", "📱 Local-only property deleted - $propertyIdToDelete")
            showSuccess("'$propertyTitle' deleted successfully")
            finalCleanupCheck(propertyIdToDelete)
        }
    }

    private fun finalCleanupCheck(propertyId: String) {
        // Double-check the property is gone from the list
        propertiesList.removeAll { it.id == propertyId }
        propertiesAdapter.notifyDataSetChanged()
        updateEmptyState()

        Log.d("MyProperties", "🔍 FINAL CHECK - List size: ${propertiesList.size}")
    }



    private fun showError(message: String) {
        Toast.makeText(this, "❌ $message", Toast.LENGTH_SHORT).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, "✅ $message", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        Log.d("MyProperties", "🔄 Activity resumed - checking properties")
    }

    override fun onDestroy() {
        super.onDestroy()
        propertiesListener?.remove()
        localBroadcastManager.unregisterReceiver(propertyUpdateReceiver)
        Log.d("MyProperties", "🔚 Activity destroyed - listener removed")

    }
}
