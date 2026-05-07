package com.example.homehub.admin

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.text.Editable
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.homehub.R
import com.example.homehub.auth.SessionManager
import com.example.homehub.databinding.ActivityManageStudentsBinding
import com.example.homehub.student.Student
import com.example.homehub.utils.toastError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ManageStudentsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageStudentsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: StudentAdapter
    private val allStudents = mutableListOf<Student>()
    private var currentUserId = ""
    private var studentsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageStudentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val sharedPref = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
        currentUserId = sharedPref.getString("userId", "") ?: ""

        setupRecyclerView()
        setupFirestoreListener()
        setupSearch()

        val filterRole = intent.getStringExtra("filter_role")
        val sessionManager = SessionManager(this)
        val userRole = sessionManager.getUserRole() ?: "Student"

        if (userRole.equals("caretaker", true)) {
            // Caretaker UI context
            binding.quickStatsContainer.visibility = View.GONE

            val filterOccupied = intent.getBooleanExtra("FILTER_OCCUPIED", false)
            val filterBooked = intent.getBooleanExtra("FILTER_BOOKED", false)
            if (filterOccupied) {
                binding.tvHeaderTitle.text = "View Tenants"
            } else if (filterBooked) {
                binding.tvHeaderTitle.text = "Reserved Tenants"
            } else {
                binding.tvHeaderTitle.text = "Your Tenants"
            }
        } 
    }

    private fun setupFirestoreListener() {
        val sessionManager = SessionManager(this)
        val userRole = sessionManager.getUserRole() ?: "Student"

        // CRITICAL BUG FIX: Caretakers do not have permission to listen to the entire 'users' collection.
        // Listening to it triggers PERMISSION_DENIED which then forces a logout.
        if (userRole.equals("caretaker", true)) {
            Log.d("ManageStudents", "Caretaker role detected, skipping global users listener to avoid permission errors.")
            loadStudents() // Initial load
            return
        }

        studentsListener?.remove()
        studentsListener = db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("ManageStudents", "Listen failed: ${error.message}")
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Toast.makeText(this, "Session expired or access denied.", Toast.LENGTH_LONG).show()
                        val intent = android.content.Intent(this, com.example.homehub.auth.UserLoginActivity::class.java)
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    return@addSnapshotListener
                }

                loadStudents()
            }
    }

    private fun setupRecyclerView() {
        val isReserved = intent.getBooleanExtra("FILTER_BOOKED", false)
        adapter = StudentAdapter(emptyList(), currentUserId, db, isReserved) {
            loadStudents()
        }
        binding.rvStudents.layoutManager = LinearLayoutManager(this)
        binding.rvStudents.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.isEmpty()) {
                    binding.btnClearSearch.visibility = View.GONE
                    adapter.updateStudents(allStudents)
                    binding.emptyState.visibility = if (allStudents.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvStudents.visibility = if (allStudents.isEmpty()) View.GONE else View.VISIBLE
                } else {
                    binding.btnClearSearch.visibility = View.VISIBLE
                    val filtered = allStudents.filter {
                        it.username.contains(query, ignoreCase = true) ||
                        it.email.contains(query, ignoreCase = true)
                    }
                    adapter.updateStudents(filtered)
                    binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvStudents.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
                }
                updateEmptyState()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text.clear()
        }
    }



    private fun loadStudents() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE

        val sessionManager = SessionManager(this)
        val userRole = sessionManager.getUserRole() ?: "Student"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val studentsList = mutableListOf<Student>()
                val seenUserIds = mutableSetOf<String>()
                val seenEmails = mutableSetOf<String>()

                if (userRole.equals("caretaker", true)) {
                    // Caretaker only sees students in their houses (via bookings)
                    val filterOccupied = intent.getBooleanExtra("FILTER_OCCUPIED", false)
                    val filterBooked = intent.getBooleanExtra("FILTER_BOOKED", false)
                    
                    val bookings = db.collection("bookings")
                        .whereEqualTo("caretakerId", currentUserId)
                        .get().await()
                    
                    val validStatuses = listOf("confirmed", "active", "paid", "pending_deferred")

                    val validBookings = bookings.documents.filter { doc ->
                        val status = doc.getString("status")?.lowercase() ?: ""
                        val isCheckedIn = doc.getBoolean("isCheckedIn") ?: false
                        
                        if (filterOccupied) {
                            isCheckedIn && (status in validStatuses || status == "completed")
                        } else if (filterBooked) {
                            !isCheckedIn && status in validStatuses
                        } else {
                            status in validStatuses || status == "completed"
                        }
                    }
                    
                    val studentIds = validBookings.mapNotNull { it.getString("studentId") }.distinct()
                    
                    for (id in studentIds) {
                        val userDoc = db.collection("users").document(id).get().await()
                        if (userDoc.exists()) {
                            val student = createStudentFromDocument(userDoc)
                            if (isValidStudent(student) && !isDuplicate(student, seenUserIds, seenEmails)) {
                                studentsList.add(student.copy(userType = "Student"))
                                addToUniqueSets(student, seenUserIds, seenEmails)
                            }
                        }
                    }
                } else {
                    // Admin sees everyone
                    loadVerifiedHosts(studentsList, seenUserIds, seenEmails)
                    loadRegularStudents(studentsList, seenUserIds, seenEmails)
                }

                val sortedStudents = studentsList.sortedWith(compareByDescending<Student> { it.userType == "Caretaker" }
                    .thenBy { it.getDisplayName().lowercase() })

                CoroutineScope(Dispatchers.Main).launch {
                    allStudents.clear()
                    allStudents.addAll(sortedStudents)

                    updateHeaderStats(sortedStudents)

                    val isReserved = intent.getBooleanExtra("FILTER_BOOKED", false)
                    adapter.updateFilterState(isReserved)
                    
                    if (sortedStudents.isEmpty()) {
                        binding.progressBar.visibility = View.GONE
                        binding.emptyState.visibility = View.VISIBLE
                        return@launch
                    }

                    adapter.updateStudents(allStudents)
                    binding.emptyState.visibility = View.GONE
                    binding.progressBar.visibility = View.GONE
                }

            } catch (e: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    binding.progressBar.visibility = View.GONE
                    binding.emptyState.visibility = View.VISIBLE
                }
            }
        }
    }

    private suspend fun loadVerifiedHosts(
        studentsList: MutableList<Student>,
        seenUserIds: MutableSet<String>,
        seenEmails: MutableSet<String>
    ) {
        try {
            val hostsSnapshot = db.collection("verifiedCaretakers").get().await()
            for (document in hostsSnapshot.documents) {
                try {
                    val host = createHostFromDocument(document)
                    if (isValidStudent(host) && !isDuplicate(host, seenUserIds, seenEmails)) {
                        val hostStudent = host.copy(userType = "Caretaker", isHostVerified = true)
                        studentsList.add(hostStudent)
                        addToUniqueSets(hostStudent, seenUserIds, seenEmails)
                    }
                } catch (e: Exception) {
                    Log.e("ManageStudents", "Error parsing host", e)
                }
            }
        } catch (e: Exception) {}
    }

    private suspend fun loadRegularStudents(
        studentsList: MutableList<Student>,
        seenUserIds: MutableSet<String>,
        seenEmails: MutableSet<String>
    ) {
        try {
            val usersSnapshot = db.collection("users").get().await()
            for (document in usersSnapshot.documents) {
                try {
                    val student = createStudentFromDocument(document)
                    if (isDuplicate(student, seenUserIds, seenEmails)) continue

                    val userTypeLower = student.userType.lowercase()
                    val finalStudent = when {
                        userTypeLower in listOf("host", "landlord", "caretaker") -> {
                            student.copy(userType = "Caretaker")
                        }
                        userTypeLower in listOf("supplier", "water_supplier", "water supplier") -> {
                            student.copy(userType = "Supplier")
                        }
                        userTypeLower == "admin" -> {
                            student.copy(userType = "Admin")
                        }
                        else -> {
                            student.copy(userType = "Student")
                        }
                    }

                    if (isValidStudent(finalStudent)) {
                        studentsList.add(finalStudent)
                        addToUniqueSets(finalStudent, seenUserIds, seenEmails)
                    }
                } catch (e: Exception) {
                    Log.e("ManageStudents", "Error parsing student", e)
                }
            }
        } catch (e: Exception) {}
    }

    private fun createHostFromDocument(document: com.google.firebase.firestore.DocumentSnapshot): Student {
        val data = document.data ?: return Student()
        val userId = (data["userId"] as? String) ?: document.id
        val fullName = (data["fullName"] as? String) ?: (data["name"] as? String) ?: ""
        val email = (data["email"] as? String) ?: ""
        val phone = (data["phone"] as? String) ?: ""

        return Student(
            userId = userId,
            username = fullName,
            email = email,
            phone = phone,
            location = "",
            status = "Active",
            userType = "Caretaker",
            isHostVerified = true,
            emailVerified = true,
            isVerified = (data["isVerified"] as? Boolean) ?: false,
            createdAt = data["createdAt"] as? com.google.firebase.Timestamp,
            lastLogin = data["lastLogin"] as? com.google.firebase.Timestamp
        )
    }

    private fun createStudentFromDocument(document: com.google.firebase.firestore.DocumentSnapshot): Student {
        val data = document.data ?: return Student()
        val userId = (data["userId"] as? String) ?: document.id
        val email = (data["email"] as? String) ?: ""
        
        // Comprehensive check for name fields
        val username = (data["fullName"] as? String) 
            ?: (data["username"] as? String) 
            ?: (data["name"] as? String) 
            ?: ""

        val status = when {
            data["status"] is String -> (data["status"] as String)
            data["accountStatus"] is String -> (data["accountStatus"] as String)
            data["isActive"] == false -> "Suspended"
            else -> "Active"
        }

        return Student(
            userId = userId,
            username = username,
            email = email,
            phone = (data["phone"] as? String) ?: "",
            location = (data["location"] as? String) ?: (data["address"] as? String) ?: "",
            registrationNumber = (data["registrationNumber"] as? String) ?: (data["studentId"] as? String) ?: "",
            bookings = (data["bookings"] as? Long)?.toInt() ?: 0,
            status = status,
            userType = (data["userType"] as? String) ?: "Student",
            createdAt = data["createdAt"] as? com.google.firebase.Timestamp,
            lastLogin = data["lastLogin"] as? com.google.firebase.Timestamp,
            isVerified = (data["isVerified"] as? Boolean) ?: false
        )
    }

    private fun isValidStudent(student: Student): Boolean {
        return student.email.isNotEmpty() && student.email.contains("@") && student.username.isNotEmpty()
    }

    private fun isDuplicate(student: Student, seenUserIds: Set<String>, seenEmails: Set<String>): Boolean {
        return (student.userId.isNotEmpty() && seenUserIds.contains(student.userId)) ||
                (student.email.isNotEmpty() && seenEmails.contains(student.email.lowercase()))
    }

    private fun addToUniqueSets(student: Student, seenUserIds: MutableSet<String>, seenEmails: MutableSet<String>) {
        if (student.userId.isNotEmpty()) seenUserIds.add(student.userId)
        if (student.email.isNotEmpty()) seenEmails.add(student.email.lowercase())
    }



    private fun updateEmptyState() {
        // Empty state is already handled in onTextChanged
    }

    private fun showStatisticsDialog() {
        val total = allStudents.size
        val active = allStudents.count { it.getStatusDisplay().equals("Active", true) }
        val suspended = allStudents.count { it.getStatusDisplay().equals("Suspended", true) }
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("User Registry Insights")
            .setMessage("Total Users: $total\nActive: $active\nSuspended: $suspended")
            .setPositiveButton("Dismiss", null)
            .show()
    }

    @SuppressLint("MissingInflatedId")
    private fun showAddStudentDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_student_admin, null)
        val fullNameInput = dialogView.findViewById<android.widget.EditText>(R.id.fullNameInput)
        val emailInput = dialogView.findViewById<android.widget.EditText>(R.id.emailInput)
        val passwordInput = dialogView.findViewById<android.widget.EditText>(R.id.passwordInput)
        val universityInput = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.universityInput)
        val typeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.studentTypeSpinner)

        val universityAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, com.example.homehub.utils.KenyanUniversities.allInstitutions.toTypedArray())
        universityInput.setAdapter(universityAdapter)
        universityInput.threshold = 1  // Start suggesting after 1 character

        val userTypes = arrayOf("student", "caretaker", "admin", "water_supplier")
        typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, userTypes)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val fullName = fullNameInput.text.toString().trim()
                val email = emailInput.text.toString().trim()
                val password = passwordInput.text.toString().trim()
                val university = universityInput.text.toString().trim()
                val type = typeSpinner.selectedItem.toString()

                if (fullName.isEmpty()) {
                    Toast.makeText(this, "Please enter full name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (password.isEmpty() || password.length < 6) {
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                checkAdminCreateUniqueness(fullName, email, password, university, type)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkAdminCreateUniqueness(fullName: String, email: String, pass: String, university: String, type: String) {
        binding.progressBar.visibility = View.VISIBLE
        db.collection("users").whereEqualTo("email", email).get().addOnSuccessListener { emailDocs ->
            if (!emailDocs.isEmpty) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Email is already in use by another user", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            
            // If it's a student, check registration number uniqueness as well (optional but good)
            createNewStudent(fullName, email, pass, university, type)
        }
    }

    private fun createNewStudent(fullName: String, email: String, pass: String, university: String, type: String) {
        binding.progressBar.visibility = View.VISIBLE
        val userId = UUID.randomUUID().toString()

        // Create Firebase Auth user first
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                if (firebaseUser != null) {
                    val userData = hashMapOf(
                        "userId" to userId,
                        "fullName" to fullName,
                        "email" to email,
                        "userType" to type,
                        "status" to "Active",
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )

                    if (university.isNotEmpty()) {
                        userData["course"] = university  // Using course field for university
                    }

                    db.collection("users").document(email).set(userData)
                        .addOnSuccessListener {
                            if (type == "caretaker") {
                                createCaretakerEntry(userId, fullName, email)
                            } else {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this, "User $fullName created successfully", Toast.LENGTH_SHORT).show()
                                loadStudents()
                            }
                        }
                        .addOnFailureListener {
                            // If Firestore fails, delete Auth user
                            firebaseUser.delete()
                            binding.progressBar.visibility = View.GONE
                            toastError(it)
                        }
                }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                toastError(it)
            }
    }

    private fun createCaretakerEntry(uid: String, name: String, email: String) {
        val caretakerData = hashMapOf(
            "userId" to uid,
            "fullName" to name,
            "email" to email,
            "isVerified" to true,
            "status" to "active",
            "joinDate" to com.google.firebase.Timestamp.now()
        )
        db.collection("verifiedCaretakers").document(uid).set(caretakerData)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Caretaker created successfully", Toast.LENGTH_SHORT).show()
                loadStudents()
            }
    }

    private fun updateHeaderStats(students: List<Student>) {
        binding.tvTotalCount.text = students.size.toString()
        binding.tvVerifiedCount.text = students.count { it.isVerified || it.isHostVerified }.toString()
        binding.tvActiveCount.text = students.count { it.userType.equals("Caretaker", true) }.toString()
    }

    override fun onResume() {
        super.onResume()
        loadStudents()
    }

    override fun onDestroy() {
        super.onDestroy()
        studentsListener?.remove()
    }
}
