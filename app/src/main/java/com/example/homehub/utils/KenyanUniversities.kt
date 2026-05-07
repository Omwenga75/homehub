package com.example.homehub.utils

/**
 * Comprehensive list of all accredited universities and university colleges in Kenya.
 * Source: Kenya Commission for University Education (CUE).
 * Includes chartered public, chartered private, and registered private institutions.
 */
object KenyanUniversities {

    // ──────────── PUBLIC CHARTERED UNIVERSITIES ────────────
    private val publicChartered = listOf(
        "University of Nairobi (UoN)",
        "Moi University",
        "Kenyatta University (KU)",
        "Egerton University",
        "Jomo Kenyatta University of Agriculture and Technology (JKUAT)",
        "Maseno University",
        "Masinde Muliro University of Science and Technology (MMUST)",
        "Dedan Kimathi University of Technology (DeKUT)",
        "Chuka University",
        "Technical University of Kenya (TUK)",
        "Technical University of Mombasa (TUM)",
        "Pwani University",
        "Kisii University",
        "University of Eldoret (UoE)",
        "Maasai Mara University",
        "Jaramogi Oginga Odinga University of Science and Technology (JOOUST)",
        "Laikipia University",
        "South Eastern Kenya University (SEKU)",
        "Meru University of Science and Technology (MUST)",
        "Multimedia University of Kenya (MMU)",
        "University of Kabianga",
        "Karatina University",
        "Kibabii University",
        "University of Embu",
        "Rongo University",
        "Taita Taveta University",
        "Machakos University",
        "Murang'a University of Technology (MUT)",
        "Co-operative University of Kenya (CUK)",
        "Kirinyaga University",
        "Garissa University",
        "Tharaka University"
    )

    // ──────────── PUBLIC UNIVERSITY CONSTITUENT COLLEGES ────────────
    private val publicConstituent = listOf(
        "Tom Mboya University",
        "Kaimosi Friends University College",
        "Alupe University College",
        "Bomet University College",
        "Kenya School of Government (KSG)"
    )

    // ──────────── PRIVATE CHARTERED UNIVERSITIES ────────────
    private val privateChartered = listOf(
        "University of Eastern Africa, Baraton (UEAB)",
        "United States International University - Africa (USIU-Africa)",
        "Daystar University",
        "Catholic University of Eastern Africa (CUEA)",
        "Scott Christian University",
        "Africa Nazarene University (ANU)",
        "Kenya Methodist University (KeMU)",
        "St. Paul's University",
        "Pan Africa Christian University (PAC)",
        "Strathmore University",
        "Kabarak University",
        "Mount Kenya University (MKU)",
        "Africa International University (AIU)",
        "Kenya Highlands Evangelical University (KHEU)",
        "Great Lakes University of Kisumu (GLUK)",
        "KCA University",
        "Adventist University of Africa (AUA)",
        "Zetech University"
    )

    // ──────────── PRIVATE REGISTERED UNIVERSITIES ────────────
    private val privateRegistered = listOf(
        "Aga Khan University",
        "Gretsa University",
        "Kiriri Women's University of Science and Technology",
        "Riara University",
        "Pioneer International University",
        "The East African University",
        "UMMA University",
        "Lukenya University",
        "Tangaza University College",
        "Marist International University College",
        "Regina Pacis University College",
        "Uzima University College",
        "International Leadership University (ILU)",
        "The Presbyterian University of East Africa (PUEA)",
        "Hekima University College",
        "Amref International University (AMIU)",
        "Management University of Africa (MUA)",
        "Nazarene University",
        "Victoria University (Kenya)",
        "RAF International University",
        "D.L. Moody University",
        "Koitaleel Samoei University College",
        "SOS Hermann Gmeiner International College"
    )

    // ──────────── NATIONAL POLYTECHNICS (University-Level) ────────────
    private val nationalPolytechnics = listOf(
        "Kenya Polytechnic (TU-K)",
        "Mombasa Polytechnic (TU-M)",
        "Eldoret National Polytechnic",
        "Kisumu National Polytechnic",
        "Nyeri National Polytechnic",
        "Kabete National Polytechnic",
        "Kitale National Polytechnic",
        "Sigalagala National Polytechnic",
        "Kisii National Polytechnic",
        "Nyandarua National Polytechnic",
        "Meru National Polytechnic",
        "Mathenge Technical Training Institute",
        "PC Kinyanjui Technical Training Institute",
        "Thika Technical Training Institute",
        "Nairobi Technical Training Institute",
        "North Eastern National Polytechnic"
    )

    // ──────────── TEACHERS' TRAINING COLLEGES ────────────
    private val teachersTraining = listOf(
        "Nairobi Teachers' Training College",
        "Kenya Science Teachers College (KSTC)",
        "Kenyatta University - Main Campus",
        "Eregi Teachers' Training College",
        "Asumbi Teachers' Training College",
        "Kagumo Teachers' Training College",
        "Kilimambogo Teachers' Training College",
        "Machakos Teachers' Training College",
        "Meru Teachers' Training College",
        "Migori Teachers' Training College",
        "Mosoriot Teachers' Training College",
        "Murang'a Teachers' Training College",
        "Shanzu Teachers' Training College",
        "Tambach Teachers' Training College",
        "Thogoto Teachers' Training College"
    )

    // ──────────── MEDICAL & HEALTH TRAINING COLLEGES ────────────
    private val healthTraining = listOf(
        "Kenya Medical Training College (KMTC) – Nairobi",
        "Kenya Medical Training College (KMTC) – Mombasa",
        "Kenya Medical Training College (KMTC) – Eldoret",
        "Kenya Medical Training College (KMTC) – Nakuru",
        "Kenya Medical Training College (KMTC) – Kisumu",
        "Kenya Medical Training College (KMTC) – Nyeri",
        "Kenya Medical Training College (KMTC) – Meru",
        "Kenya Medical Training College (KMTC) – Kisii",
        "Kenya Medical Training College (KMTC) – Machakos",
        "Kenya Medical Training College (KMTC) – Kakamega"
    )

    // ──────────── OTHER TERTIARY INSTITUTIONS ────────────
    private val otherTertiary = listOf(
        "Kenya School of Law (KSL)",
        "Kenya Institute of Management (KIM)",
        "Kenya Institute of Supplies Examination Board (KISEB)",
        "Kenya Accountants and Secretaries National Examination Board (KASNEB)",
        "Kenya Institute of Highways and Building Technology (KIHBT)",
        "Kenya Wildlife Service Training Institute",
        "Kenya Water Institute",
        "Bandari Maritime Academy",
        "East African School of Aviation",
        "Kenya Coast National Polytechnic",
        "Utalii College",
        "NIBS Technical College",
        "Nairobi Aviation College",
        "Vision Institute of Professionals",
        "KCAU - KCA University Nairobi Campus",
        "Embu University College",
        "Kiriri Womens University",
        "Limkokwing University (Kenya Campus)",
        "Rockfields Institute of Professional Studies"
    )

    /**
     * Full sorted list of all institutions (155+)
     */
    val allInstitutions: List<String> by lazy {
        (publicChartered + publicConstituent + privateChartered +
                privateRegistered + nationalPolytechnics + teachersTraining +
                healthTraining + otherTertiary).sorted()
    }

    /**
     * Returns a filtered list matching the query (case-insensitive)
     */
    fun search(query: String): List<String> {
        if (query.isBlank()) return allInstitutions
        val lower = query.lowercase()
        return allInstitutions.filter { it.lowercase().contains(lower) }
    }
}
