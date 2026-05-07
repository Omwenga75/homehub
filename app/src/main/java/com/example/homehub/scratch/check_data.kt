
import com.example.homehub.property.Property
import com.google.firebase.firestore.FirebaseFirestore

fun checkProperties() {
    val db = FirebaseFirestore.getInstance()
    db.collection("properties").get().addOnSuccessListener { snapshot ->
        println("Total Properties: ${snapshot.size()}")
        for (doc in snapshot.documents) {
            val p = Property.fromDocument(doc.data ?: emptyMap())
            println("ID: ${p.id}, Title: ${p.title}, Type: ${p.propertyType}, Category: ${p.category}")
        }
    }.addOnFailureListener {
        println("Failed to fetch properties: ${it.message}")
    }
}
