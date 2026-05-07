package com.example.homehub.billing

object RentRiskService {

    fun analyzeRisk(booking: Booking, history: List<Booking>): RiskAnalysis {
        // Simple logic for simulation:
        // Use the mpesaReceiptNumber as a seed for consistent scoring in demo
        val seed = booking.mpesaReceiptNumber.hashCode().toDouble()
        val score = (40 + (seed % 55).toInt().coerceAtLeast(0)).toDouble().coerceAtMost(100.0)
        
        val level = when {
            score >= 85 -> RiskLevel.LOW
            score >= 60 -> RiskLevel.MEDIUM
            else -> RiskLevel.HIGH
        }

        val reasons = mutableListOf<String>()
        if (score >= 85) {
            reasons.add("Perfect payment consistency")
            reasons.add("Zero reported disputes")
        } else if (score >= 60) {
            reasons.add("Mostly on-time payments")
            reasons.add("Occasional delay patterns detected")
        } else {
            reasons.add("Irregular payment cycles")
            reasons.add("Frequent late submissions")
        }

        return RiskAnalysis(score.toInt(), level, reasons)
    }

    fun calculateRisk(): Double = 0.0
}

data class RiskAnalysis(
    val score: Int,
    val level: RiskLevel,
    val reasons: List<String>
)

enum class RiskLevel(val label: String, val colorHex: String) {
    LOW("Low Risk", "#00A86B"),
    MEDIUM("Medium Risk", "#FFA000"),
    HIGH("High Risk", "#D32F2F")
}

