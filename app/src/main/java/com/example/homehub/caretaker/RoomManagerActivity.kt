package com.example.homehub.caretaker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.example.homehub.property.Room
import com.example.homehub.property.RoomAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.*
import java.util.*

class RoomManagerActivity : AppCompatActivity() {

    private lateinit var propertyId: String
    private lateinit var propertyTitle: String
    private val db = FirebaseFirestore.getInstance()
    private var roomsListener: ListenerRegistration? = null
    private lateinit var adapter: RoomAdapter
    private val roomsList = mutableListOf<Room>()
    
    private lateinit var tvTitle: TextView
    private lateinit var tvStats: TextView
    private lateinit var rvRooms: RecyclerView
    private lateinit var fabAdd: ExtendedFloatingActionButton
    private lateinit var progressBar: ProgressBar

    private var selectedImages = mutableListOf<Uri>()
    private var imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.clipData?.let { for (i in 0 until it.itemCount) selectedImages.add(it.getItemAt(i).uri) }
            ?: result.data?.data?.let { selectedImages.add(it) }
            Toast.makeText(this, "${selectedImages.size} images selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_manager)

        propertyId = intent.getStringExtra("PROPERTY_ID") ?: return
        propertyTitle = intent.getStringExtra("PROPERTY_TITLE") ?: "Property Rooms"

        initializeViews()
        setupRecyclerView()
        loadRooms()
    }

    private fun initializeViews() {
        findViewById<TextView>(R.id.propertyTitle).text = propertyTitle
        tvStats = findViewById(R.id.propertyStats)
        rvRooms = findViewById(R.id.rvRooms)
        fabAdd = findViewById(R.id.fabAddRoom)
        progressBar = findViewById(R.id.progressBar)
        
        fabAdd.setOnClickListener { showAddRoomDialog() }
    }

    private fun setupRecyclerView() {
        adapter = RoomAdapter(roomsList, 
            onEdit = { showAddRoomDialog(it) },
            onDelete = { deleteRoom(it) },
            onToggleAvailability = { room, available -> updateRoomAvailability(room, available) },
            onItemClick = { room ->
                if (!room.isAvailable) {
                    val intent = Intent(this, CaretakerStudentRoomActivity::class.java).apply {
                        putExtra("ROOM_ID", room.id)
                        putExtra("PROPERTY_ID", room.propertyId)
                        putExtra("ROOM_NO", room.roomNumber)
                        // Mocking the possibility of a request based on random chance or you can toggle it
                        putExtra("REQUESTED_LEAVE", true)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Room is currently vacant.", Toast.LENGTH_SHORT).show()
                }
            }
        )
        rvRooms.layoutManager = LinearLayoutManager(this)
        rvRooms.adapter = adapter
    }

    private fun loadRooms() {
        progressBar.visibility = View.VISIBLE
        roomsListener = db.collection("rooms")
            .whereEqualTo("propertyId", propertyId)
            .orderBy("roomNumber", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                progressBar.visibility = View.GONE
                if (e != null) return@addSnapshotListener
                
                roomsList.clear()
                snapshot?.documents?.forEach { roomsList.add(Room.fromDocument(it.data ?: emptyMap()).apply { id = it.id }) }
                adapter.updateRooms(roomsList)
                updatePropertyStats()
            }
    }

    private fun updatePropertyStats() {
        val total = roomsList.size
        val available = roomsList.count { it.isAvailable }
        tvStats.text = "$total Rooms total · $available Available"
        
        // Update parent property document
        db.collection("properties").document(propertyId)
            .update(mapOf("totalRooms" to total, "availableRooms" to available))
    }

    private fun showAddRoomDialog(editingRoom: Room? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_room, null)
        val etNumber = dialogView.findViewById<TextInputEditText>(R.id.etRoomNumber)
        val spType = dialogView.findViewById<Spinner>(R.id.spRoomType)
        val etPrice = dialogView.findViewById<TextInputEditText>(R.id.etPrice)
        val etDesc = dialogView.findViewById<TextInputEditText>(R.id.etDescription)
        val btnImages = dialogView.findViewById<MaterialButton>(R.id.btnSelectImages)
        
        val types = listOf("Bedsitter", "1 Bedroom", "2 Bedroom", "Studio", "Single Room", "Double Room")
        spType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, types)

        selectedImages.clear()
        if (editingRoom != null) {
            etNumber.setText(editingRoom.roomNumber)
            etPrice.setText(editingRoom.price.toString())
            etDesc.setText(editingRoom.description)
            spType.setSelection(types.indexOf(editingRoom.roomType).coerceAtLeast(0))
        }

        btnImages.setOnClickListener {
            imagePickerLauncher.launch(Intent.createChooser(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) }, "Select Images"))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (editingRoom == null) "Add New Room" else "Edit Room")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val number = etNumber.text.toString().trim()
                val priceStr = etPrice.text.toString().trim()
                if (number.isEmpty() || priceStr.isEmpty()) return@setPositiveButton
                
                val room = editingRoom ?: Room(id = UUID.randomUUID().toString(), propertyId = propertyId)
                room.apply {
                    roomNumber = number
                    roomType = spType.selectedItem.toString()
                    price = priceStr.toDoubleOrNull() ?: 0.0
                    description = etDesc.text.toString()
                    updatedAt = Date()
                }
                saveRoom(room)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveRoom(room: Room) {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            if (selectedImages.isNotEmpty()) {
                room.imageUrls = selectedImages.mapNotNull { uri ->
                    val bytes = contentResolver.openInputStream(uri)?.readBytes()
                    if (bytes != null) "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.DEFAULT)}" else null
                }
            }
            
            db.collection("rooms").document(room.id).set(room.toMap())
                .addOnSuccessListener {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@RoomManagerActivity, "Room saved!", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun deleteRoom(room: Room) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Room")
            .setMessage("Are you sure you want to remove room ${room.roomNumber}?")
            .setPositiveButton("Delete") { _, _ ->
                db.collection("rooms").document(room.id).delete()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateRoomAvailability(room: Room, available: Boolean) {
        db.collection("rooms").document(room.id).update("isAvailable", available)
            .addOnSuccessListener { updatePropertyStats() }
    }

    override fun onDestroy() {
        super.onDestroy()
        roomsListener?.remove()
    }
}
