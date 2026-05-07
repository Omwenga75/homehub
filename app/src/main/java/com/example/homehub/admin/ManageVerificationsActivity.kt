package com.example.homehub.admin

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ManageVerificationsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var verificationsRecyclerView: RecyclerView
    private lateinit var noApplicationsText: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack: android.widget.ImageButton

    private lateinit var verificationsAdapter: VerificationsAdapter
    private var applicationsListener: ListenerRegistration? = null
    private val allVerifications = mutableListOf<VerificationRequest>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_verifications)

        try {
            db = FirebaseFirestore.getInstance()
            initializeViews()
            setupRecyclerView()
            loadVerifications()
        } catch (e: Exception) {
            Log.e("ManageVerifications", "Error in onCreate: ${e.message}", e)
            showToast("Error initializing application")
        }
    }

    private fun initializeViews() {
        verificationsRecyclerView = findViewById(R.id.verificationsRecyclerView)
        noApplicationsText = findViewById(R.id.noApplicationsText)
        progressBar = findViewById(R.id.progressBar)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener {
            finish()
        }
    }



    private fun setupRecyclerView() {
        verificationsAdapter = VerificationsAdapter(emptyList()) { verification, documentId ->
            showVerificationDetails(documentId)
        }
        verificationsRecyclerView.layoutManager = LinearLayoutManager(this)
        verificationsRecyclerView.adapter = verificationsAdapter
    }

    private fun loadVerifications() {
        progressBar.visibility = View.VISIBLE

        applicationsListener = db.collection("verificationRequests")
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE

                if (error != null) {
                    Log.e("ManageVerifications", "Firestore error: ${error.message}", error)
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Toast.makeText(this, "Session expired or access denied.", Toast.LENGTH_LONG).show()
                        val intent = Intent(this, com.example.homehub.auth.UserLoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else if (allVerifications.isEmpty()) {
                        showToast("Error loading verification requests")
                    }
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    return@addSnapshotListener
                }

                allVerifications.clear()

                if (!snapshot.isEmpty) {
                    for (document in snapshot.documents) {
                        try {
                            val data = document.data
                            if (data != null) {
                                val verification = VerificationRequest.fromDocument(document.id, data)
                                allVerifications.add(verification)
                            }
                        } catch (e: Exception) {
                            Log.e("ManageVerifications", "Error parsing document ${document.id}", e)
                        }
                    }
                    
                    // Sort by submission date (newest first)
                    allVerifications.sortByDescending { it.submittedAt }
                }

                verificationsAdapter.updateVerifications(allVerifications)
                updateUI()
            }
    }

    private fun showVerificationDetails(documentId: String) {
        val intent = Intent(this, VerificationDetailsActivity::class.java)
        intent.putExtra("APPLICANT_ID", documentId)
        startActivity(intent)
    }

    private fun updateUI() {
        val count = verificationsAdapter.itemCount

        if (count == 0) {
            noApplicationsText.visibility = View.VISIBLE
            verificationsRecyclerView.visibility = View.GONE
        } else {
            noApplicationsText.visibility = View.GONE
            verificationsRecyclerView.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        applicationsListener?.remove()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
