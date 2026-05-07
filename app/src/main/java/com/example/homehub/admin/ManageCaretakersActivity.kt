package com.example.homehub.admin

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.homehub.databinding.ActivityManageCaretakersBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.homehub.R
import com.example.homehub.admin.CaretakersAdapter
import com.example.homehub.caretaker.Caretaker

class ManageCaretakersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageCaretakersBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: CaretakersAdapter
    private val allCaretakers = mutableListOf<Caretaker>()
    private var caretakersListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageCaretakersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)

        db = FirebaseFirestore.getInstance()

        setupRecyclerView()
        setupSearch()
        setupFirestoreListener()

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = CaretakersAdapter(emptyList(), db) {
            loadCaretakers()
        }
        binding.rvCaretakers.layoutManager = LinearLayoutManager(this)
        binding.rvCaretakers.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterCaretakers(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterCaretakers(query: String) {
        val filtered = if (query.isEmpty()) {
            allCaretakers
        } else {
            allCaretakers.filter {
                it.getDisplayName().contains(query, ignoreCase = true) ||
                it.email.contains(query, ignoreCase = true) ||
                it.phone.contains(query, ignoreCase = true)
            }
        }
        adapter.updateCaretakers(filtered)
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        updateStats(filtered)
    }

    private fun setupFirestoreListener() {
        binding.progressBar.visibility = View.VISIBLE
        caretakersListener = db.collection("users")
            .whereEqualTo("role", "caretaker")
            .addSnapshotListener { snapshot, error ->
                binding.progressBar.visibility = View.GONE
                if (error != null) {
                    android.util.Log.e("ManageCaretakers", "Error loading caretakers: ${error.message}")
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Toast.makeText(this, "Session expired or access denied.", Toast.LENGTH_LONG).show()
                        val intent = Intent(this, com.example.homehub.auth.UserLoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "Error loading caretakers", Toast.LENGTH_SHORT).show()
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    loadCaretakers()
                }
            }
    }

    private fun loadCaretakers() {
        db.collection("users")
            .whereEqualTo("role", "caretaker")
            .get()
            .addOnSuccessListener { snapshot ->
                allCaretakers.clear()
                for (doc in snapshot.documents) {
                    try {
                        val data = doc.data ?: continue
                        val caretaker = Caretaker.fromDocument(data)
                        allCaretakers.add(caretaker)
                    } catch (e: Exception) {
                        // skip malformed doc
                    }
                }

                val sorted = allCaretakers.sortedByDescending { it.isVerified }
                allCaretakers.clear()
                allCaretakers.addAll(sorted)

                adapter.updateCaretakers(allCaretakers)
                binding.emptyState.visibility = if (allCaretakers.isEmpty()) View.VISIBLE else View.GONE
                updateStats(allCaretakers)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load caretakers", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateStats(list: List<Caretaker>) {
        binding.tvTotalCount.text = list.size.toString()
        binding.tvVerifiedCount.text = list.count { it.isVerified }.toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        caretakersListener?.remove()
    }
}
