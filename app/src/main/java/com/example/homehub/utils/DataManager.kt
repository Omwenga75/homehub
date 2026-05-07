package com.example.homehub.utils

import android.content.Context
import android.util.Log
import com.example.homehub.caretaker.Caretaker
import com.example.homehub.caretaker.CaretakerApplication
import com.example.homehub.property.Property
import com.example.homehub.student.Student
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

object DataManager {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val _scope = CoroutineScope(Dispatchers.IO)

    private val _caretakers = MutableStateFlow<List<Caretaker>>(emptyList())
    private val _properties = MutableStateFlow<List<Property>>(emptyList())
    private val _applications = MutableStateFlow<List<CaretakerApplication>>(emptyList())
    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    private val _userProfile = MutableStateFlow<Student?>(null)
    private val _caretakerProfile = MutableStateFlow<Caretaker?>(null)

    private var caretakersListener: ListenerRegistration? = null
    private var propertiesListener: ListenerRegistration? = null
    private var applicationsListener: ListenerRegistration? = null
    private var notificationsListener: ListenerRegistration? = null
    private var userProfileListener: ListenerRegistration? = null
    private var caretakerProfileListener: ListenerRegistration? = null

    val caretakers: StateFlow<List<Caretaker>> = _caretakers.asStateFlow()
    val properties: StateFlow<List<Property>> = _properties.asStateFlow()
    val applications: StateFlow<List<CaretakerApplication>> = _applications.asStateFlow()
    val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()
    val userProfile: StateFlow<Student?> = _userProfile.asStateFlow()
    val caretakerProfile: StateFlow<Caretaker?> = _caretakerProfile.asStateFlow()

    fun initialize(context: Context) {
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        Log.d("DataManager", "✅ Initialized DataManager")
    }

    fun startCaretakersListener() {
        caretakersListener?.remove()
        caretakersListener = db.collection("verifiedCaretakers")
            .addSnapshotListener { snapshot, error ->
                snapshot?.let { querySnapshot ->
                    val caretakersList = querySnapshot.documents.mapNotNull { it.toObject<Caretaker>()?.copy(userId = it.id) }
                    _caretakers.value = caretakersList
                }
            }
    }

    fun startPropertiesListener(userId: String? = null) {
        propertiesListener?.remove()
        val query = if (userId != null) db.collection("properties").whereEqualTo("caretakerId", userId) else db.collection("properties")
        propertiesListener = query.addSnapshotListener { snapshot, error ->
            snapshot?.let { querySnapshot ->
                val propertiesList = querySnapshot.documents.mapNotNull { it.toObject<Property>()?.copy(id = it.id) }
                _properties.value = propertiesList
            }
        }
    }

    fun stopAllListeners() {
        caretakersListener?.remove()
        propertiesListener?.remove()
        applicationsListener?.remove()
        notificationsListener?.remove()
        userProfileListener?.remove()
        caretakerProfileListener?.remove()
    }
}
