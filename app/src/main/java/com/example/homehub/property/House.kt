package com.example.homehub.property

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize

@Parcelize
data class House(
    @get:PropertyName("houseId") @set:PropertyName("houseId")
    var houseId: String = "",
    var title: String = "",
    var type: String = "",
    var description: String = "",
    var price: Double = 0.0,
    var currency: String = "Sh.",
    var location: String = "",
    var bedrooms: Int = 0,
    var bathrooms: Int = 0,
    var area: Int = 20,
    var imageUrl: String = "",
    var imageUrls: List<String> = emptyList(),
    var isAvailable: Boolean = true,
    var isFeatured: Boolean = false,
    var ownerId: String = "",
    var ownerName: String = "",
    var rating: Float? = null,
    var reviewsCount: Int = 0,
    var amenities: List<String> = emptyList(),
    var features: List<String> = emptyList(),
    var createdAt: Timestamp = Timestamp.now(),
    var updatedAt: Timestamp = Timestamp.now(),
    var caretakerProfilePicture: String = "",
    var caretakerId: String = "",
    var status: String = "Active",
    var caretakerVerified: Boolean = false,
    var firebaseImages: List<String> = emptyList(),
    var roomImages: Map<String, List<String>> = emptyMap()
) : Parcelable
