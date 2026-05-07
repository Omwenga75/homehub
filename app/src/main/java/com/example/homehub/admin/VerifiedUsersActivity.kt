package com.example.homehub.admin

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.homehub.databinding.ActivityVerifiedUsersBinding
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.homehub.R
import com.example.homehub.admin.StudentAdapter
import com.example.homehub.student.Student

class VerifiedUsersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerifiedUsersBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: StudentAdapter
    private var currentUserId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerifiedUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)
        db = FirebaseFirestore.getInstance()

        val sharedPref = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
        currentUserId = sharedPref.getString("userId", "") ?: ""

        setupRecyclerView()

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadVerifiedUsers()
        }
    }

    private fun setupRecyclerView() {
        adapter = StudentAdapter(emptyList(), currentUserId, db) {
            loadVerifiedUsers()
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadVerifiedUsers() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val studentsList = mutableListOf<Student>()
                val seenUserIds = mutableSetOf<String>()
                val seenEmails = mutableSetOf<String>()

                // Load Verified Caretakers
                val hostsSnapshot = db.collection("verifiedCaretakers").get().await()
                for (doc in hostsSnapshot.documents) {
                    val host = createStudentFromVerification(doc, true)
                    if (isValidStudent(host) && !isDuplicate(host, seenUserIds, seenEmails)) {
                        studentsList.add(host)
                        addToUniqueSets(host, seenUserIds, seenEmails)
                    }
                }

                // Load Regular Users
                val usersSnapshot = db.collection("users").whereEqualTo("isVerified", true).get().await()
                for (doc in usersSnapshot.documents) {
                    val student = createStudentFromVerification(doc, false)
                    if (isValidStudent(student) && !isDuplicate(student, seenUserIds, seenEmails)) {
                        studentsList.add(student)
                        addToUniqueSets(student, seenUserIds, seenEmails)
                    }
                }

                val sortedStudents = studentsList.sortedBy { it.getDisplayName().lowercase() }

                CoroutineScope(Dispatchers.Main).launch {
                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.progressBar.visibility = View.GONE
                    
                    if (sortedStudents.isEmpty()) {
                        binding.emptyState.visibility = View.VISIBLE
                    } else {
                        binding.emptyState.visibility = View.GONE
                        adapter.updateStudents(sortedStudents)
                    }
                }

            } catch (e: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.progressBar.visibility = View.GONE
                    binding.emptyState.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun createStudentFromVerification(document: com.google.firebase.firestore.DocumentSnapshot, isHost: Boolean): Student {
        val data = document.data ?: return Student()
        val userId = (data["userId"] as? String) ?: document.id
        val fullName = (data["fullName"] as? String) ?: (data["name"] as? String) ?: (data["username"] as? String) ?: ""
        val email = (data["email"] as? String) ?: ""
        val phone = (data["phone"] as? String) ?: ""
        val type = if (isHost) "Caretaker" else (data["userType"] as? String) ?: "Student"

        val status = when {
            data["status"] is String -> (data["status"] as String)
            data["accountStatus"] is String -> (data["accountStatus"] as String)
            data["isActive"] == false -> "Suspended"
            else -> "Active"
        }

        return Student(
            userId = userId,
            username = fullName,
            email = email,
            phone = phone,
            location = "",
            status = status,
            userType = type,
            isHostVerified = isHost,
            isVerified = true,
            createdAt = data["createdAt"] as? com.google.firebase.Timestamp,
            lastLogin = data["lastLogin"] as? com.google.firebase.Timestamp
        )
    }

    private fun isValidStudent(student: Student): Boolean {
        return student.email.isNotEmpty() && student.username.isNotEmpty()
    }

    private fun isDuplicate(student: Student, seenUserIds: Set<String>, seenEmails: Set<String>): Boolean {
        return (student.userId.isNotEmpty() && seenUserIds.contains(student.userId)) ||
                (student.email.isNotEmpty() && seenEmails.contains(student.email.lowercase()))
    }

    private fun addToUniqueSets(student: Student, seenUserIds: MutableSet<String>, seenEmails: MutableSet<String>) {
        if (student.userId.isNotEmpty()) seenUserIds.add(student.userId)
        if (student.email.isNotEmpty()) seenEmails.add(student.email.lowercase())
    }

    override fun onResume() {
        super.onResume()
        loadVerifiedUsers()
    }
}
