package com.example.homehub.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*
import com.example.homehub.caretaker.CaretakerApplication
import com.example.homehub.utils.NotificationManager

class CaretakerApplicationViewModel : ViewModel() {
    private val db: FirebaseFirestore = Firebase.firestore
    private var applicationListener: ListenerRegistration? = null

    private val _application = MutableLiveData<CaretakerApplication>()
    val application: LiveData<CaretakerApplication> = _application

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _statusUpdateSuccess = MutableLiveData<Boolean>()
    val statusUpdateSuccess: LiveData<Boolean> = _statusUpdateSuccess

    fun fetchApplicationById(applicationId: String) {
        _isLoading.value = true
        _error.value = null

        applicationListener?.remove()

        applicationListener = db.collection("caretakerApplications")
            .document(applicationId)
            .addSnapshotListener { snapshot, exception ->
                _isLoading.value = false

                if (exception != null) {
                    _error.value = "Error fetching application: ${exception.message}"
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    try {
                        val application = CaretakerApplication.fromDocument(snapshot.id, snapshot.data!!)
                        _application.value = application
                    } catch (e: Exception) {
                        _error.value = "Error parsing application data: ${e.message}"
                    }
                } else {
                    _error.value = "Application not found"
                }
            }
    }

    fun updateApplicationStatus(
        applicationId: String,
        newStatus: String,
        reviewedBy: String,
        rejectionReason: String = "",
        notes: String = ""
    ) {
        _isLoading.value = true
        _error.value = null
        _statusUpdateSuccess.value = false

        val updateData = hashMapOf<String, Any>(
            "status" to newStatus,
            "reviewedBy" to reviewedBy,
            "reviewedAt" to FieldValue.serverTimestamp()
        )

        if (newStatus == CaretakerApplication.STATUS_REJECTED && rejectionReason.isNotEmpty()) {
            updateData["rejectionReason"] = rejectionReason
        }

        if (notes.isNotEmpty()) {
            updateData["notes"] = notes
        }

        viewModelScope.launch {
            try {
                db.collection("caretakerApplications")
                    .document(applicationId)
                    .update(updateData)
                    .await()

                val currentApp = _application.value
                if (currentApp != null) {
                    val updatedApp = currentApp.copy(
                        status = newStatus,
                        reviewedBy = reviewedBy,
                        reviewedAt = Date().time,
                        rejectionReason = if (newStatus == CaretakerApplication.STATUS_REJECTED) rejectionReason else currentApp.rejectionReason,
                        notes = if (notes.isNotEmpty()) notes else currentApp.notes
                    )
                    _application.value = updatedApp
                }

                _statusUpdateSuccess.value = true

                val userId = currentApp?.userId ?: ""
                val applicantName = currentApp?.fullName ?: ""
                if (userId.isNotEmpty()) {
                    when (newStatus) {
                        CaretakerApplication.STATUS_APPROVED -> {
                            NotificationManager.sendApplicationApprovedNotification(userId, applicantName)
                        }
                        CaretakerApplication.STATUS_REJECTED -> {
                            NotificationManager.sendApplicationRejectedNotification(userId, applicantName, rejectionReason)
                        }
                    }
                }

            } catch (e: Exception) {
                _error.value = "Error updating status: ${e.message}"
                _statusUpdateSuccess.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun approveApplication(applicationId: String, reviewerName: String) {
        updateApplicationStatus(
            applicationId = applicationId,
            newStatus = CaretakerApplication.STATUS_APPROVED,
            reviewedBy = reviewerName
        )
    }
    fun rejectApplication(applicationId: String, reviewerName: String, reason: String) {
        updateApplicationStatus(
            applicationId = applicationId,
            newStatus = CaretakerApplication.STATUS_REJECTED,
            reviewedBy = reviewerName,
            rejectionReason = reason
        )
    }

    override fun onCleared() {
        super.onCleared()
        applicationListener?.remove()
    }
}
