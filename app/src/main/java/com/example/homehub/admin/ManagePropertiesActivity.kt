package com.example.homehub.admin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.util.Date
import com.example.homehub.R
import com.example.homehub.property.Property
import com.example.homehub.property.UnifiedPropertyAdapter
import com.example.homehub.utils.StatusEntry
import com.example.homehub.property.AddPropertyActivity

class ManagePropertiesActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var propertiesRecyclerView: RecyclerView
    private lateinit var noPropertiesLayout: LinearLayout
    private lateinit var noPropertiesText: TextView
    private lateinit var filterSpinner: Spinner
    private lateinit var searchEditText: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var totalPropertiesText: TextView
    private lateinit var activePropertiesText: TextView
    private lateinit var rentedPropertiesText: TextView
    private lateinit var suspendedPropertiesText: TextView

    private lateinit var propertiesAdapter: UnifiedPropertyAdapter
    private var propertiesListener: ListenerRegistration? = null
    private val properties = mutableListOf<Property>()
    private var allProperties = mutableListOf<Property>()

    // Broadcast receiver for property updates
    private lateinit var localBroadcastManager: LocalBroadcastManager
    private val propertyUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("ManageProperties", "📢 Received property update broadcast")
            // Refresh data when properties are updated elsewhere
            loadProperties()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_properties)

        db = FirebaseFirestore.getInstance()
        localBroadcastManager = LocalBroadcastManager.getInstance(this)

        initializeViews()
        setupFilterSpinner()
        setupRecyclerView()
        setupSearchListener()
        loadProperties()

        // Register broadcast receiver
        registerBroadcastReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        propertiesListener?.remove()
        localBroadcastManager.unregisterReceiver(propertyUpdateReceiver)
        try {
            unregisterReceiver(propertyUpdateReceiver)
        } catch (e: Exception) {}
        Log.d("ManageProperties", "Activity destroyed")
    }

    private fun registerBroadcastReceiver() {
        val filter = IntentFilter().apply {
            addAction("PROPERTY_STATUS_UPDATED")
            addAction("PROPERTY_DELETED")
        }
        localBroadcastManager.registerReceiver(propertyUpdateReceiver, filter)
        registerReceiver(propertyUpdateReceiver, filter)
    }

    private fun initializeViews() {
        propertiesRecyclerView = findViewById(R.id.propertiesRecyclerView)
        noPropertiesLayout = findViewById(R.id.noPropertiesLayout)
        noPropertiesText = findViewById(R.id.noPropertiesText)
        filterSpinner = findViewById(R.id.filterSpinner)
        searchEditText = findViewById(R.id.searchEditText)
        progressBar = findViewById(R.id.progressBar)
        totalPropertiesText = findViewById(R.id.totalPropertiesText)
        activePropertiesText = findViewById(R.id.activePropertiesText)
        rentedPropertiesText = findViewById(R.id.rentedPropertiesText)
        suspendedPropertiesText = findViewById(R.id.suspendedPropertiesText)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<ImageButton>(R.id.addPropertyButton).setOnClickListener {
            startActivity(Intent(this, AddPropertyActivity::class.java))
        }
    }

    private fun setupFilterSpinner() {
        val filterOptions = arrayOf("All Properties", "Active", "Suspended", "Rented", "Deleted", "Available")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filterSpinner.adapter = adapter

        filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterProperties(filterOptions[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupRecyclerView() {
        propertiesAdapter = UnifiedPropertyAdapter(
            layoutResId = R.layout.item_property1,
            onItemClick = { property, _ ->
                showAdminActionsDialog(property)
            },
            onFavoriteClick = null,
            onMoreClick = null
        )
        propertiesRecyclerView.layoutManager = LinearLayoutManager(this)
        propertiesRecyclerView.adapter = propertiesAdapter
        propertiesAdapter.submitList(ArrayList(properties))
    }

    private fun setupSearchListener() {
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                searchProperties(s.toString())
            }
        })
    }

    private fun loadProperties() {
        progressBar.visibility = View.VISIBLE
        propertiesRecyclerView.visibility = View.GONE
        noPropertiesLayout.visibility = View.GONE

        propertiesListener?.remove()

        propertiesListener = db.collection("properties")
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE

                if (error != null) {
                    Log.e("ManageProperties", "Error loading properties: ${error.message}")
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Log.e("ManageProperties", "CRITICAL: Insufficient Permission. Redirecting to login.")
                        Toast.makeText(this, "Session expired or insufficient permission.", Toast.LENGTH_LONG).show()
                        val intent = Intent(this, com.example.homehub.auth.UserLoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        showToast("Error loading properties: ${error.message}")
                    }
                    updateUI()
                    return@addSnapshotListener
                }

                allProperties.clear()
                if (snapshot != null && !snapshot.isEmpty) {
                    for (document in snapshot) {
                        try {
                            val propertyData = document.data.toMutableMap()
                            propertyData["id"] = document.id
                            val property = Property.fromDocument(propertyData)
                            allProperties.add(property)
                            Log.d("ManageProperties", "Loaded property: ${property.displayTitle} - Status: ${property.status}, Deleted: ${property.isDeleted}")
                        } catch (e: Exception) {
                            Log.e("ManageProperties", "Error parsing property ${document.id}: ${e.message}")
                        }
                    }
                }

                updateStats()
                filterProperties(filterSpinner.selectedItem.toString())
                updateUI()
                Log.d("ManageProperties", "Total properties loaded: ${allProperties.size}")
            }
    }

    private fun updateStats() {
        val total = allProperties.size

        // Active properties (not suspended, not deleted, available)
        val active = allProperties.count {
            (it.status?.lowercase() == "active" || it.status?.lowercase() == "available") &&
                    !it.isDeleted && !it.isSuspendedByAdmin()
        }

        // Suspended properties (by admin)
        val suspended = allProperties.count {
            it.isSuspendedByAdmin() && !it.isDeleted
        }

        // Actual rented properties (by caretaker, not suspended)
        val actualRented = allProperties.count {
            (it.status?.lowercase() == "rented" ||
                    it.status?.lowercase() == "booked" ||
                    it.status?.lowercase() == "occupied") &&
                    !it.isDeleted && !it.isSuspendedByAdmin()
        }

        // Update all statistics
        totalPropertiesText.text = total.toString()
        activePropertiesText.text = active.toString()
        rentedPropertiesText.text = actualRented.toString()  // Show actual rented (not suspended)
        suspendedPropertiesText.text = suspended.toString()  // Show suspended count

        Log.d("ManageProperties", "Stats: Total=$total, Active=$active, Suspended=$suspended, ActualRented=$actualRented")
    }

    private fun filterProperties(filter: String) {
        val filteredProperties = when (filter) {
            "Active" -> allProperties.filter {
                (it.status?.lowercase() == "active" || it.status?.lowercase() == "available") &&
                        !it.isDeleted && !it.isSuspendedByAdmin()
            }
            "Suspended" -> allProperties.filter {
                it.isSuspendedByAdmin() && !it.isDeleted  // Only show suspended by admin
            }
            "Rented" -> allProperties.filter {
                (it.status?.lowercase() == "rented" ||
                        it.status?.lowercase() == "booked" ||
                        it.status?.lowercase() == "occupied") &&
                        !it.isDeleted && !it.isSuspendedByAdmin()  // Actual rented, not suspended
            }
            "Deleted" -> allProperties.filter {
                it.isDeleted
            }
            "Available" -> allProperties.filter {
                (it.status?.lowercase() == "active" || it.status?.lowercase() == "available") &&
                        !(it.status?.lowercase() == "rented" ||
                                it.status?.lowercase() == "booked" ||
                                it.status?.lowercase() == "occupied") &&
                        !it.isDeleted && !it.isSuspendedByAdmin()
            }
            else -> allProperties.filter { !it.isDeleted } // "All Properties" - exclude deleted
        }

        properties.clear()
        properties.addAll(filteredProperties)
        propertiesAdapter.submitList(ArrayList(properties))

        Log.d("ManageProperties", "Filter '$filter': Showing ${filteredProperties.size} of ${allProperties.size} properties")
    }

    private fun searchProperties(query: String) {
        if (query.isEmpty()) {
            filterProperties(filterSpinner.selectedItem.toString())
            return
        }

        val lowercaseQuery = query.lowercase().trim()
        if (lowercaseQuery.isEmpty()) {
            filterProperties(filterSpinner.selectedItem.toString())
            return
        }

        val searchResults = allProperties.filter { property ->
            (property.displayTitle.lowercase().contains(lowercaseQuery) ||
                    property.description?.lowercase()?.contains(lowercaseQuery) == true ||
                    property.location.lowercase().contains(lowercaseQuery) ||
                    property.propertyType.lowercase().contains(lowercaseQuery) ||
                    property.caretakerName?.lowercase()?.contains(lowercaseQuery) == true ||
                    property.ownerName?.lowercase()?.contains(lowercaseQuery) == true ||
                    property.price?.lowercase()?.contains(lowercaseQuery) == true) &&
                    !property.isDeleted
        }

        properties.clear()
        properties.addAll(searchResults)
        propertiesAdapter.submitList(ArrayList(properties))

        Log.d("ManageProperties", "Search '$query': Found ${searchResults.size} properties")
    }

    private fun updateUI() {
        if (properties.isEmpty()) {
            noPropertiesLayout.visibility = View.VISIBLE
            propertiesRecyclerView.visibility = View.GONE
            noPropertiesText.text = "No properties found"
        } else {
            noPropertiesLayout.visibility = View.GONE
            propertiesRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showAdminActionsDialog(property: Property) {
        val isDeleted = property.isDeleted
        val isSuspended = property.isSuspendedByAdmin()

        val options = if (!isDeleted) {
            if (isSuspended) {
                arrayOf(
                    "View Details",
                    "Activate Property",
                    "Soft Delete Property",
                    "Permanently Delete Property",
                    "View Bookings"
                )
            } else {
                arrayOf(
                    "View Details",
                    "Suspend Property",
                    "Soft Delete Property",
                    "Permanently Delete Property",
                    "View Bookings"
                )
            }
        } else {
            arrayOf(
                "View Details",
                "Restore Property",
                "Permanently Delete",
                "View Bookings"
            )
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (isDeleted) "Manage Deleted Property" else "Manage Property")
            .setItems(options) { dialog, which ->
                when {
                    isDeleted -> handleDeletedPropertyActions(which, property)
                    else -> handleActivePropertyActions(which, property, isSuspended)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun handleActivePropertyActions(which: Int, property: Property, isSuspended: Boolean) {
        when (which) {
            0 -> showPropertyDetails(property)
            1 -> if (isSuspended) activateProperty(property) else suspendProperty(property)
            2 -> deleteProperty(property) // Soft delete
            3 -> permanentlyDeleteProperty(property)
            4 -> viewBookings(property)
        }
    }

    private fun handleDeletedPropertyActions(which: Int, property: Property) {
        when (which) {
            0 -> showPropertyDetails(property)
            1 -> restoreProperty(property)
            2 -> permanentlyDeleteProperty(property)
            3 -> viewBookings(property)
        }
    }

    private fun suspendProperty(property: Property) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Suspend Property")
            .setMessage("⚠️ Suspend '${property.displayTitle}'?\n\nThis will:\n• Hide property from user dashboard\n• Prevent new bookings\n• Show as 'Suspended' in caretaker's properties\n• Rented functionality remains in caretaker dashboard")
            .setPositiveButton("Suspend") { dialog, _ ->
                performSuspendProperty(property)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun performSuspendProperty(property: Property) {
        progressBar.visibility = View.VISIBLE

        val updates = hashMapOf<String, Any>(
            "status" to "Inactive",
            "available" to false,
            "adminAction" to true,
            "inactiveAt" to FieldValue.serverTimestamp(),
            "inactiveBy" to "admin",
            "statusChangeReason" to "Suspended by admin",
            "updatedAt" to FieldValue.serverTimestamp()
        )

        // Add to status history
        val statusHistory = property.statusHistory.toMutableList()
        statusHistory.add(StatusEntry(
            status = "Inactive",
            reason = "Suspended by admin",
            changedBy = "admin",
            changedAt = Date()
        ))

        updates["statusHistory"] = statusHistory

        db.collection("properties").document(property.id)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                showToast("'${property.displayTitle}' suspended")
                Log.d("ManageProperties", "✅ Property suspended: ${property.id}")

                // Update local data
                updateLocalPropertyStatus(property.id, "Inactive", false, true, "admin")

                // Send broadcast to update other activities
                sendPropertyUpdateBroadcast(property.id, "suspended")
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                showToast("Failed to suspend property: ${e.message}")
                Log.e("ManageProperties", "❌ Error suspending property: ${e.message}")
            }
    }

    private fun activateProperty(property: Property) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Activate Property")
            .setMessage("Activate '${property.displayTitle}'?\n\nThis will:\n• Show property on user dashboard\n• Allow bookings\n• Update host profile statistics")
            .setPositiveButton("Activate") { dialog, _ ->
                performActivateProperty(property)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun performActivateProperty(property: Property) {
        progressBar.visibility = View.VISIBLE

        val updates = hashMapOf<String, Any>(
            "status" to "Active",
            "available" to true,
            "adminAction" to false,
            "reactivatedAt" to FieldValue.serverTimestamp(),
            "statusChangeReason" to "Activated by admin",
            "updatedAt" to FieldValue.serverTimestamp()
        )

        // Add to status history
        val statusHistory = property.statusHistory.toMutableList()
        statusHistory.add(StatusEntry(
            status = "Active",
            reason = "Activated by admin",
            changedBy = "admin",
            changedAt = Date()
        ))

        updates["statusHistory"] = statusHistory

        db.collection("properties").document(property.id)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                showToast("'${property.displayTitle}' activated")
                Log.d("ManageProperties", "✅ Property activated: ${property.id}")

                // Update local data
                updateLocalPropertyStatus(property.id, "Active", true, false, "")

                // Send broadcast to update other activities
                sendPropertyUpdateBroadcast(property.id, "activated")
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                showToast("Failed to activate property: ${e.message}")
                Log.e("ManageProperties", "❌ Error activating property: ${e.message}")
            }
    }

    private fun deleteProperty(property: Property) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Property")
            .setMessage("Are you sure you want to delete '${property.displayTitle}'?\n\n" +
                    "⚠️ This will:\n" +
                    "• Mark property as 'Inactive' in caretaker profile\n" +
                    "• Hide property from user dashboard\n" +
                    "• Keep property data for record keeping\n" +
                    "• Cancel all active bookings")
            .setPositiveButton("Soft Delete") { dialog, _ ->
                checkForActiveBookingsBeforeDelete(property)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun checkForActiveBookingsBeforeDelete(property: Property) {
        progressBar.visibility = View.VISIBLE

        db.collection("bookings")
            .whereEqualTo("propertyId", property.id)
            .whereIn("status", listOf("confirmed", "active", "pending"))
            .get()
            .addOnSuccessListener { snapshot ->
                progressBar.visibility = View.GONE

                if (!snapshot.isEmpty) {
                    val activeBookingsCount = snapshot.size()
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Active Bookings Found")
                        .setMessage("This property has $activeBookingsCount active booking(s).\n\nWhat would you like to do?")
                        .setPositiveButton("Cancel All & Delete") { dialog, _ ->
                            cancelAllBookingsAndDelete(property, snapshot.documents)
                            dialog.dismiss()
                        }
                        .setNegativeButton("Keep Property") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .setNeutralButton("View Bookings") { dialog, _ ->
                            viewBookings(property)
                            dialog.dismiss()
                        }
                        .show()
                } else {
                    showFinalDeleteConfirmation(property)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                showToast("Error checking bookings: ${e.message}")
                Log.e("ManageProperties", "Error checking bookings: ${e.message}")
            }
    }

    private fun cancelAllBookingsAndDelete(property: Property, bookingDocuments: List<com.google.firebase.firestore.DocumentSnapshot>) {
        progressBar.visibility = View.VISIBLE

        val batch = db.batch()
        val updates = hashMapOf<String, Any>(
            "status" to "cancelled",
            "cancelledAt" to FieldValue.serverTimestamp(),
            "cancelledBy" to "admin",
            "cancellationReason" to "Property deleted by admin"
        )

        bookingDocuments.forEach { document ->
            val bookingRef = db.collection("bookings").document(document.id)
            batch.update(bookingRef, updates)
        }

        batch.commit()
            .addOnSuccessListener {
                softDeleteProperty(property)
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                showToast("Failed to cancel bookings: ${e.message}")
                Log.e("ManageProperties", "Error cancelling bookings: ${e.message}")
            }
    }

    private fun showFinalDeleteConfirmation(property: Property) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Final Confirmation")
            .setMessage("⚠️ This is a SOFT DELETE. The property will:\n" +
                    "• Be marked as 'Inactive' in caretaker profile\n" +
                    "• Be hidden from user dashboard\n" +
                    "• Remain in database for records\n\n" +
                    "Are you sure you want to proceed with soft delete?")
            .setPositiveButton("Soft Delete") { dialog, _ ->
                softDeleteProperty(property)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun softDeleteProperty(property: Property) {
        progressBar.visibility = View.VISIBLE

        // First update UI immediately
        val propertyIndex = allProperties.indexOfFirst { it.id == property.id }
        if (propertyIndex != -1) {
            allProperties[propertyIndex].isDeleted = true
            allProperties[propertyIndex].status = "Booked"
            allProperties[propertyIndex].available = false
        }
        filterProperties(filterSpinner.selectedItem.toString())
        updateStats()
        updateUI()

        // Then update Firebase with SOFT DELETE (mark as deleted but keep data)
        val updates = hashMapOf<String, Any>(
            "isDeleted" to true,
            "status" to "Booked", // Count as booked in caretaker profile
            "available" to false,
            "deletedAt" to FieldValue.serverTimestamp(),
            "deletedBy" to "admin",
            "deletionReason" to "Soft delete by admin",
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("properties").document(property.id)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                showToast("'${property.displayTitle}' soft deleted (marked as booked)")
                Log.d("ManageProperties", "✅ Property soft deleted: ${property.id}")

                // Send broadcast to update other activities
                sendPropertyUpdateBroadcast(property.id, "soft_deleted")

                // Send delete broadcast
                sendDeleteBroadcast(property.id)
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                showToast("Failed to delete property: ${e.message}")
                Log.e("ManageProperties", "❌ Error soft deleting property: ${e.message}")

                // Revert UI changes if Firebase failed
                if (propertyIndex != -1) {
                    allProperties[propertyIndex].isDeleted = false
                    filterProperties(filterSpinner.selectedItem.toString())
                    updateStats()
                }
            }
    }

    private fun restoreProperty(property: Property) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Restore Property")
            .setMessage("Restore '${property.displayTitle}'?\n\nThis will:\n• Make property active again\n• Show on user dashboard\n• Update caretaker profile statistics")
            .setPositiveButton("Restore") { dialog, _ ->
                performRestoreProperty(property)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun performRestoreProperty(property: Property) {
        progressBar.visibility = View.VISIBLE

        // First update UI
        val propertyIndex = allProperties.indexOfFirst { it.id == property.id }
        if (propertyIndex != -1) {
            allProperties[propertyIndex].isDeleted = false
            allProperties[propertyIndex].status = "Active"
            allProperties[propertyIndex].available = true
        }
        filterProperties(filterSpinner.selectedItem.toString())
        updateStats()
        updateUI()

        // Then update Firebase
        val updates = hashMapOf<String, Any>(
            "isDeleted" to false,
            "status" to "Active",
            "available" to true,
            "restoredAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("properties").document(property.id)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                showToast("'${property.displayTitle}' restored successfully")
                Log.d("ManageProperties", "✅ Property restored: ${property.id}")

                // Send broadcast
                sendPropertyUpdateBroadcast(property.id, "restored")
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                showToast("Failed to restore property: ${e.message}")
                Log.e("ManageProperties", "❌ Error restoring property: ${e.message}")
            }
    }

    private fun permanentlyDeleteProperty(property: Property) {
        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ Permanent Delete")
            .setMessage("❗ WARNING: This will PERMANENTLY delete '${property.displayTitle}'!\n\n" +
                    "This action:\n• Cannot be undone\n• Will remove all property data\n• Will delete from database\n• Will update caretaker profile")
            .setPositiveButton("Permanently Delete") { dialog, _ ->
                confirmPermanentDelete(property)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun confirmPermanentDelete(property: Property) {
        val editText = EditText(this).apply {
            hint = "Type DELETE to confirm"
            setPadding(32, 16, 32, 16)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Final Warning")
            .setMessage("This will permanently erase all data for '${property.displayTitle}'. Type 'DELETE' to confirm:")
            .setView(editText)
            .setPositiveButton("Delete Forever") { dialog, _ ->
                val confirmationText = editText.text.toString().trim()
                if (confirmationText.equals("DELETE", ignoreCase = true)) {
                    deletePropertyPermanently(property)
                } else {
                    showToast("Confirmation failed. Type 'DELETE' exactly.")
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deletePropertyPermanently(property: Property) {
        progressBar.visibility = View.VISIBLE

        // First remove from UI
        allProperties.removeAll { it.id == property.id }
        properties.removeAll { it.id == property.id }
        propertiesAdapter.submitList(ArrayList(properties))
        updateStats()
        updateUI()

        // Then delete from Firebase
        db.collection("properties").document(property.id)
            .delete()
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                showToast("'${property.displayTitle}' permanently deleted")
                Log.d("ManageProperties", "✅ Property permanently deleted: ${property.id}")

                // Send broadcast
                sendPropertyUpdateBroadcast(property.id, "permanently_deleted")
                sendDeleteBroadcast(property.id)
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                showToast("Failed to delete property: ${e.message}")
                Log.e("ManageProperties", "❌ Error permanently deleting property: ${e.message}")
            }
    }

    private fun updateLocalPropertyStatus(propertyId: String, newStatus: String, isAvailable: Boolean, adminAction: Boolean, changedBy: String) {
        // Update local list
        val propertyIndex = allProperties.indexOfFirst { it.id == propertyId }
        if (propertyIndex != -1) {
            allProperties[propertyIndex].status = newStatus
            allProperties[propertyIndex].available = isAvailable
            allProperties[propertyIndex].adminAction = adminAction

            if (newStatus == "Inactive") {
                allProperties[propertyIndex].inactiveBy = changedBy
                allProperties[propertyIndex].inactiveAt = Date()
            } else if (newStatus == "Active") {
                allProperties[propertyIndex].reactivatedAt = Date()
            }

            filterProperties(filterSpinner.selectedItem.toString())
            updateStats()
        }
    }

    private fun sendPropertyUpdateBroadcast(propertyId: String, action: String) {
        // Send local broadcast for activities within the app
        val localIntent = Intent("PROPERTY_STATUS_UPDATED").apply {
            putExtra("propertyId", propertyId)
            putExtra("action", action)
            putExtra("timestamp", System.currentTimeMillis())
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent)

        // Send system broadcast for broader notification
        val systemIntent = Intent().apply {
            setAction("com.example.homehub.PROPERTY_ADMIN_UPDATE")
            putExtra("propertyId", propertyId)
            putExtra("adminAction", action)
            putExtra("timestamp", System.currentTimeMillis())
            putExtra("appPackage", packageName)
        }

        // Send the broadcast
        sendBroadcast(systemIntent)

        Log.d("ManageProperties", "📢 Sent broadcast: $action for property $propertyId")
    }

    private fun sendDeleteBroadcast(propertyId: String) {
        val intent = Intent("PROPERTY_DELETED").apply {
            putExtra("propertyId", propertyId)
        }
        localBroadcastManager.sendBroadcast(intent)
    }

    private fun showPropertyDetails(property: Property) {
        Log.d("ManageProperties", "Showing details for property: ${property.id}")
        // Placeholder for property details navigation
    }

    private fun viewBookings(property: Property) {
        // Placeholder for bookings navigation
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
