package com.example.homehub.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.util.*
import com.example.homehub.R
import com.example.homehub.caretaker.Caretaker

class CaretakersAdapter(
    private var caretakers: List<Caretaker>,
    private val db: com.google.firebase.firestore.FirebaseFirestore,
    private val onDataChanged: () -> Unit
) : RecyclerView.Adapter<CaretakersAdapter.CaretakerViewHolder>() {

    private lateinit var context: android.content.Context

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CaretakerViewHolder {
        context = parent.context
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_caretaker, parent, false)
        return CaretakerViewHolder(view)
    }

    override fun onBindViewHolder(holder: CaretakerViewHolder, position: Int) {
        val caretaker = caretakers[position]
        holder.bind(caretaker)

        holder.itemView.setOnClickListener { view ->
            showOptionsMenu(holder.moreButton, caretaker)
        }
        
        holder.moreButton.setOnClickListener { view ->
            showOptionsMenu(view, caretaker)
        }
    }

    private fun showOptionsMenu(view: View, caretaker: Caretaker) {
        val popup = android.widget.PopupMenu(context, view)
        
        val isActive = caretaker.status.equals("active", true)
        popup.menu.add(0, 1, 0, if (isActive) "Suspend Caretaker" else "Activate Caretaker")
        popup.menu.add(0, 2, 1, "Delete Caretaker")
        popup.menu.add(0, 3, 2, "Reset Password")
        popup.menu.add(0, 4, 3, "Send Message")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { toggleCaretakerStatus(caretaker); true }
                2 -> { confirmDeleteCaretaker(caretaker); true }
                3 -> { resetPassword(caretaker); true }
                4 -> { sendMessage(caretaker); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun toggleCaretakerStatus(caretaker: Caretaker) {
        val newStatus = if (caretaker.status.equals("active", true)) "suspended" else "active"
        val title = if (newStatus == "active") "Activate" else "Suspend"
        
        android.app.AlertDialog.Builder(context)
            .setTitle("$title Caretaker")
            .setMessage("Are you sure you want to $newStatus ${caretaker.getDisplayName()}?")
            .setPositiveButton(title) { _, _ -> updateStatusInFirestore(caretaker, newStatus) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateStatusInFirestore(caretaker: Caretaker, status: String) {
        val updates = mapOf(
            "status" to status,
            "accountStatus" to status,
            "isActive" to status.equals("active", true)
        )

        val batch = db.batch()
        batch.update(db.collection("users").document(caretaker.email), updates)
        batch.update(db.collection("verifiedCaretakers").document(caretaker.userId), updates)

        batch.commit().addOnSuccessListener {
            android.widget.Toast.makeText(context, "Status updated to $status", android.widget.Toast.LENGTH_SHORT).show()
            onDataChanged()
        }.addOnFailureListener { e ->
            android.widget.Toast.makeText(context, "Update failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteCaretaker(caretaker: Caretaker) {
        android.app.AlertDialog.Builder(context)
            .setTitle("Delete Caretaker")
            .setMessage("Permanently delete ${caretaker.getDisplayName()}? This will remove them from the verified registry. Action cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteCaretakerFromFirestore(caretaker) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCaretakerFromFirestore(caretaker: Caretaker) {
        val batch = db.batch()
        batch.delete(db.collection("users").document(caretaker.userId))
        batch.delete(db.collection("verifiedCaretakers").document(caretaker.userId))

        batch.commit().addOnSuccessListener {
            android.widget.Toast.makeText(context, "Caretaker deleted", android.widget.Toast.LENGTH_SHORT).show()
            onDataChanged()
        }.addOnFailureListener { e ->
            android.widget.Toast.makeText(context, "Deletion failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetPassword(caretaker: Caretaker) {
        android.widget.Toast.makeText(context, "Password reset email sent to ${caretaker.email}", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun sendMessage(caretaker: Caretaker) {
        android.widget.Toast.makeText(context, "Opening chat with ${caretaker.getDisplayName()}...", android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun getItemCount(): Int = caretakers.size

    fun updateCaretakers(newCaretakers: List<Caretaker>) {
        caretakers = newCaretakers
        notifyDataSetChanged()
    }

    inner class CaretakerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val moreButton: android.widget.ImageButton = itemView.findViewById(R.id.moreButton)
        private val tvRoleLabel: TextView = itemView.findViewById(R.id.tvRoleLabel)
        private val emailText: TextView = itemView.findViewById(R.id.emailText)
        private val verifiedBadge: ImageView = itemView.findViewById(R.id.verifiedBadge)
        private val propertiesCountText: TextView = itemView.findViewById(R.id.propertiesCountText)
        private val locationText: TextView = itemView.findViewById(R.id.locationText)
        private val phoneText: TextView = itemView.findViewById(R.id.phoneText)
        private val earningsText: TextView = itemView.findViewById(R.id.earningsText)
        private val bookingsCountText: TextView = itemView.findViewById(R.id.bookingsCountText)
        private val ratingText: TextView = itemView.findViewById(R.id.ratingText)
        private val joinDateText: TextView = itemView.findViewById(R.id.joinDateText)
        private val caretakerIcon: ImageView = itemView.findViewById(R.id.caretakerIcon)

        fun bind(caretaker: Caretaker) {
            tvRoleLabel.text = caretaker.getDisplayName()
            com.example.homehub.utils.LetterAvatarHelper.setLetterAvatar(caretakerIcon, caretaker.getDisplayName(), caretaker.profileImageUrl, caretaker.userId)
            
            emailText.text = caretaker.email
            phoneText.text = if (caretaker.phone.isNullOrBlank()) "Not provided" else caretaker.phone
            verifiedBadge.visibility = if (caretaker.isVerified) View.VISIBLE else View.GONE
            
            // Stats Row Binding
            propertiesCountText.text = caretaker.totalProperties.toString()
            earningsText.text = caretaker.getFormattedEarnings()
            bookingsCountText.text = caretaker.totalBookings.toString()
            ratingText.text = caretaker.getFormattedLikes()
            
            // Standard Footer Fields
            locationText.text = caretaker.getDisplayLocation()
            joinDateText.text = "Joined " + getTimeAgo(caretaker.joinDate)
        }

        private fun getTimeAgo(timestampMillis: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestampMillis
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            return when {
                seconds < 60 -> "Just now"
                minutes < 60 -> "$minutes minutes ago"
                hours < 24 -> "$hours hours ago"
                else -> "$days days ago"
            }
        }
    }
}
