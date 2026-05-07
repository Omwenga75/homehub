package com.example.homehub.admin

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.homehub.R
import com.example.homehub.caretaker.CaretakerApplication
import com.example.homehub.admin.ApplicationsAdapter

class ManageApplicationsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_UPDATE_APPLICATION = 1001
    }

    private lateinit var db: FirebaseFirestore
    private lateinit var applicationsRecyclerView: RecyclerView
    private lateinit var noApplicationsText: TextView
    private lateinit var filterSpinner: Spinner
    private lateinit var progressBar: ProgressBar
    private lateinit var applicationsCount: TextView
    private lateinit var addCaretakerButton: android.widget.ImageButton
    private lateinit var btnBack: android.widget.ImageButton

    private lateinit var applicationsAdapter: ApplicationsAdapter
    private var applicationsListener: ListenerRegistration? = null
    private val allApplications = mutableListOf<CaretakerApplication>()
    private val documentIds = mutableListOf<String>() // Store document IDs
    private var currentFilter = "All Applications" // Track current filter

    private val reviewApplicationLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // Data refreshes automatically via Firestore listener
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_applications)

        try {
            db = FirebaseFirestore.getInstance()
            initializeViews()
            setupFilterSpinner()
            setupRecyclerView()
            loadApplications()
        } catch (e: Exception) {
            Log.e("ManageApplications", "Error in onCreate: ${e.message}", e)
            showToast("Error initializing application")
        }
    }

    private fun initializeViews() {
        try {
            applicationsRecyclerView = findViewById(R.id.applicationsRecyclerView)
            noApplicationsText = findViewById(R.id.noApplicationsText)
            filterSpinner = findViewById(R.id.filterSpinner)
            progressBar = findViewById(R.id.progressBar)
            applicationsCount = findViewById(R.id.applicationsCount)

            btnBack = findViewById(R.id.btnBack)
            btnBack.setOnClickListener {
                finish()
            }

            addCaretakerButton = findViewById(R.id.addCaretakerButton)
            addCaretakerButton.setOnClickListener {
                // Navigate to add caretaker or show dialog
                showToast("Add caretaker functionality")
            }

            Log.d("ManageApplications", "All views initialized successfully")
        } catch (e: Exception) {
            Log.e("ManageApplications", "Error initializing views: ${e.message}", e)
        }
    }

    private fun setupFilterSpinner() {
        try {
            val filterOptions = arrayOf("All Applications", "Pending", "Approved", "Rejected")
            val adapter = ArrayAdapter(this, R.layout.spinner_item, filterOptions)
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            filterSpinner.adapter = adapter

            filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    currentFilter = filterOptions[position]
                    filterApplications(currentFilter)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        } catch (e: Exception) {
            Log.e("ManageApplications", "Error setting up spinner: ${e.message}", e)
        }
    }

    private fun setupRecyclerView() {
        try {
            // FIXED: Now passing lambda with TWO parameters (application, documentId)
            applicationsAdapter = ApplicationsAdapter(emptyList()) { application, documentId ->
                showApplicationDetails(application, documentId)
            }
            applicationsRecyclerView.layoutManager = LinearLayoutManager(this)
            applicationsRecyclerView.adapter = applicationsAdapter
        } catch (e: Exception) {
            Log.e("ManageApplications", "Error setting up recycler view: ${e.message}", e)
        }
    }

    private fun loadApplications() {
        progressBar.visibility = View.VISIBLE
        Log.d("ManageApplications", "Starting to load applications...")

        applicationsListener = db.collection("caretakerApplications")
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE

                if (error != null) {
                    Log.e("ManageApplications", "Firestore error: ${error.message}", error)
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Toast.makeText(this, "Session expired or access denied.", Toast.LENGTH_LONG).show()
                        val intent = Intent(this, com.example.homehub.auth.UserLoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else if (allApplications.isEmpty()) {
                        showToast("Error loading applications")
                    }
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    Log.e("ManageApplications", "Snapshot is null")
                    if (allApplications.isEmpty()) {
                        showToast("No data received")
                    }
                    return@addSnapshotListener
                }

                Log.d("ManageApplications", "Snapshot received. Empty: ${snapshot.isEmpty}, Size: ${snapshot.documents.size}")

                allApplications.clear()
                documentIds.clear()

                if (!snapshot.isEmpty) {
                    for (document in snapshot.documents) {
                        try {
                            Log.d("ManageApplications", "Processing document: ${document.id}")
                            val applicationData = document.data
                            if (applicationData != null) {
                                // FIXED: Use the updated fromDocument method with documentId
                                val application = CaretakerApplication.fromDocument(document.id, applicationData)
                                allApplications.add(application)
                                documentIds.add(document.id) // Store the document ID
                                Log.d("ManageApplications", "Successfully loaded: ${application.fullName} - ${application.status} (Doc ID: ${application.documentId})")
                            } else {
                                Log.w("ManageApplications", "Document data is null for: ${document.id}")
                            }
                        } catch (e: Exception) {
                            Log.e("ManageApplications", "Error parsing document ${document.id}: ${e.message}", e)
                        }
                    }
                    // Sort by applicationDate
                    allApplications.sortByDescending { it.applicationDate }

                    Log.d("ManageApplications", "Total applications loaded: ${allApplications.size}")
                } else {
                    Log.d("ManageApplications", "No applications found in database")
                }

                updateUI()
                filterApplications(currentFilter) // Apply current filter
            }
    }

    // FIXED: Now accepts documentId parameter
    private fun showApplicationDetails(application: CaretakerApplication, documentId: String) {
        try {
            val intent = Intent(this, ApplicationDetailsActivity::class.java)
            intent.putExtra("application", application)

            // Use the application's documentId instead of the separate parameter
            // (or keep both for redundancy)
            val actualDocumentId = application.documentId.ifEmpty { documentId }
            intent.putExtra("documentId", actualDocumentId)

            Log.d("ManageApplications", "Opening details with document ID: $actualDocumentId")
            Log.d("ManageApplications", "Application status: ${application.status}")

            reviewApplicationLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("ManageApplications", "Error showing application details: ${e.message}", e)
            showToast("Error opening application details")
        }
    }

    private fun filterApplications(filter: String) {
        val filteredApplications = when (filter) {
            "Pending" -> allApplications.filter { it.isPending() }
            "Approved" -> allApplications.filter { it.isApproved() }
            "Rejected" -> allApplications.filter { it.isRejected() }
            else -> allApplications
        }

        applicationsAdapter.updateApplications(filteredApplications)
        updateUI()
    }

    private fun updateUI() {
        try {
            val count = applicationsAdapter.itemCount
            applicationsCount.text = "Total Verifications ($count)"

            if (count == 0) {
                noApplicationsText.visibility = View.VISIBLE
                applicationsRecyclerView.visibility = View.GONE
            } else {
                noApplicationsText.visibility = View.GONE
                applicationsRecyclerView.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e("ManageApplications", "Error updating UI: ${e.message}", e)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_UPDATE_APPLICATION && resultCode == RESULT_OK) {
            // Refresh applications when returning from details activity with updates
            // The Firestore listener will automatically update the data
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh filter when returning to activity
        filterApplications(currentFilter)
    }

    override fun onDestroy() {
        super.onDestroy()
        applicationsListener?.remove()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
