package com.example.homehub.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.homehub.databinding.FragmentUserListBinding
import com.example.homehub.student.Student
import com.example.homehub.caretaker.Caretaker
import com.example.homehub.supplier.WaterSupplier
import com.example.homehub.supplier.WaterSupplierAdapter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.example.homehub.auth.SessionManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserListFragment : Fragment() {

    private var _binding: FragmentUserListBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: FirebaseFirestore
    private var userRole: String = ""
    private var currentUserId: String = ""

    private var studentAdapter: StudentAdapter? = null
    private var caretakerAdapter: CaretakersAdapter? = null
    private var supplierAdapter: WaterSupplierAdapter? = null

    private var allUsers = mutableListOf<Any>()
    private var filteredUsers = mutableListOf<Any>()

    companion object {
        private const val ARG_ROLE = "user_role"

        fun newInstance(role: String): UserListFragment {
            val fragment = UserListFragment()
            val args = Bundle()
            args.putString(ARG_ROLE, role)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userRole = arguments?.getString(ARG_ROLE) ?: ""
        db = FirebaseFirestore.getInstance()
        currentUserId = SessionManager(requireContext()).getUserId() ?: ""
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeRefresh()
        loadData()
    }

    private fun setupRecyclerView() {
        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        
        when (userRole) {
            "admin", "student" -> {
                studentAdapter = StudentAdapter(emptyList(), currentUserId, db) {
                    loadData()
                }
                binding.rvUsers.adapter = studentAdapter
            }
            "caretaker" -> {
                caretakerAdapter = CaretakersAdapter(emptyList(), db) {
                    loadData()
                }
                binding.rvUsers.adapter = caretakerAdapter
            }
            "supplier" -> {
                supplierAdapter = WaterSupplierAdapter(emptyList(),
                    isAdmin = true,
                    onItemClick = { supplier ->
                        Toast.makeText(requireContext(), "Supplier: ${supplier.businessName}", Toast.LENGTH_SHORT).show()
                    },
                    onCallClick = { supplier -> /* Handle call */ },
                    onChatClick = { supplier -> /* Handle chat */ },
                    onSuspendClick = { supplier -> toggleSupplierStatus(supplier) },
                    onDeleteClick = { supplier -> confirmDeleteSupplier(supplier) },
                    onResetPasswordClick = { supplier -> 
                        Toast.makeText(requireContext(), "Password reset email sent to ${supplier.email}", Toast.LENGTH_SHORT).show()
                    }
                )
                binding.rvUsers.adapter = supplierAdapter
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadData()
        }
    }

    fun loadData() {
        if (!isAdded) return
        
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            android.util.Log.e("UserListFragment", "User is NOT authenticated in Firebase. Redirecting or showing error.")
            binding.progressBar.visibility = View.GONE
            binding.swipeRefreshLayout.isRefreshing = false
            Toast.makeText(requireContext(), "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }

        android.util.Log.d("UserListFragment", "Authenticated User UID: ${currentUser.uid}")
        binding.progressBar.visibility = View.VISIBLE
        
        // Use a simpler query structure to avoid index issues
        val usersRef = db.collection("users")
        val query = usersRef

        query.get().addOnSuccessListener { snapshot ->
            if (!isAdded) return@addOnSuccessListener
            
            lifecycleScope.launch(Dispatchers.Default) {
                val newUsers = mutableListOf<Any>()
                val seenUserIds = mutableSetOf<String>()
                val seenEmails = mutableSetOf<String>()

                for (doc in snapshot.documents) {
                    try {
                        val userId = doc.id
                        val data = doc.data ?: continue
                        val roleField = (data["role"] as? String)?.lowercase() ?: ""
                        val typeField = (data["userType"] as? String)?.lowercase() ?: ""
                        val email = (data["email"] as? String)?.lowercase() ?: ""

                        // Deduplication: Skip if we've seen this user (by ID or Email)
                        if (seenUserIds.contains(userId) || (email.isNotEmpty() && seenEmails.contains(email))) {
                            continue
                        }
                        
                        val detectedRole = when {
                            roleField == "admin" || typeField == "admin" || email == "admin@homehub.com" -> "admin"
                            roleField == "caretaker" || typeField == "caretaker" || typeField == "host" -> "caretaker"
                            roleField == "supplier" || roleField == "water_supplier" || typeField == "supplier" || typeField == "water_supplier" || typeField == "water supplier" -> "supplier"
                            else -> "student"
                        }

                        // Only add if it belongs in the current tab's role
                        // Note: userRole comes from ARG_ROLE (student, caretaker, supplier)
                        if (detectedRole == userRole) {
                            when (detectedRole) {
                                "admin", "student" -> {
                                    val student = doc.toObject(Student::class.java)?.copy(userId = userId)
                                    if (student != null) {
                                        newUsers.add(student)
                                        seenUserIds.add(userId)
                                        if (email.isNotEmpty()) seenEmails.add(email)
                                    }
                                }
                                "caretaker" -> {
                                    val caretaker = Caretaker.fromDocument(data).copy(userId = userId)
                                    newUsers.add(caretaker)
                                    seenUserIds.add(userId)
                                    if (email.isNotEmpty()) seenEmails.add(email)
                                }
                                "supplier" -> {
                                    val supplier = WaterSupplier.fromDocument(userId, data)
                                    newUsers.add(supplier)
                                    seenUserIds.add(userId)
                                    if (email.isNotEmpty()) seenEmails.add(email)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("UserListFragment", "Error parsing user ${doc.id}: ${e.message}")
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                    
                    allUsers.clear()
                    allUsers.addAll(newUsers)
                    
                    filteredUsers.clear()
                    filteredUsers.addAll(allUsers)
                    updateUI()
                }
            }
        }.addOnFailureListener { e ->
            if (!isAdded) return@addOnFailureListener
            binding.progressBar.visibility = View.GONE
            binding.swipeRefreshLayout.isRefreshing = false
            
            val errorMsg = e.message ?: "Unknown error"
            android.util.Log.e("UserListFragment", "Permission Denied or Firestore Error: $errorMsg", e)
            
            if (e is com.google.firebase.firestore.FirebaseFirestoreException && 
                e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                
                Toast.makeText(requireContext(), "Access Denied: Admin privileges required", Toast.LENGTH_LONG).show()
                // Auto-redirect to login if permission is permanently lost
                val intent = Intent(requireContext(), com.example.homehub.auth.UserLoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                activity?.finish()
            } else {
                Toast.makeText(requireContext(), "Failed to load $userRole: $errorMsg", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun filter(query: String) {
        filteredUsers.clear()
        if (query.isEmpty()) {
            filteredUsers.addAll(allUsers)
        } else {
            for (user in allUsers) {
                val matches = when (user) {
                    is Student -> user.getDisplayName().contains(query, true) || user.email.contains(query, true)
                    is Caretaker -> user.fullName.contains(query, true) || user.email.contains(query, true)
                    is WaterSupplier -> user.businessName.contains(query, true) || user.name.contains(query, true)
                    else -> false
                }
                if (matches) filteredUsers.add(user)
            }
        }

        updateUI()
    }

    private fun toggleSupplierStatus(supplier: WaterSupplier) {
        val newStatus = if (supplier.status.equals("active", true)) "suspended" else "active"
        val title = if (newStatus == "active") "Activate" else "Suspend"
        
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("$title Supplier")
            .setMessage("Are you sure you want to $newStatus ${supplier.businessName}?")
            .setPositiveButton(title) { _, _ ->
                val updates = mapOf("status" to newStatus, "accountStatus" to newStatus)
                val batch = db.batch()
                batch.update(db.collection("users").document(supplier.id), updates)
                batch.update(db.collection("waterSuppliers").document(supplier.id), updates)
                
                batch.commit().addOnSuccessListener {
                    Toast.makeText(requireContext(), "Status updated to $newStatus", Toast.LENGTH_SHORT).show()
                    loadData()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteSupplier(supplier: WaterSupplier) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Supplier")
            .setMessage("Permanently delete ${supplier.businessName}? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                val batch = db.batch()
                batch.delete(db.collection("users").document(supplier.id))
                batch.delete(db.collection("waterSuppliers").document(supplier.id))
                
                batch.commit().addOnSuccessListener {
                    Toast.makeText(requireContext(), "Supplier deleted", Toast.LENGTH_SHORT).show()
                    loadData()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateUI() {
        binding.emptyState.visibility = if (filteredUsers.isEmpty()) View.VISIBLE else View.GONE
        
        when (userRole) {
            "admin", "student" -> studentAdapter?.updateStudents(filteredUsers as List<Student>)
            "caretaker" -> caretakerAdapter?.updateCaretakers(filteredUsers as List<Caretaker>)
            "supplier" -> supplierAdapter?.updateSuppliers(filteredUsers as List<WaterSupplier>)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
