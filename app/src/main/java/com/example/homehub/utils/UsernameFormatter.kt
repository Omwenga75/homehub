package com.example.homehub.utils

import java.util.Locale

object UsernameFormatter {

    private val commonPrefixes = listOf(
        "admin", "support", "info", "contact", "hello", "mail",
        "sales", "service", "team", "user", "test", "demo"
    )

    private val knownNameMappings = mapOf(
        "mosalah" to "Mo Salah",
        "kiranzukiroza" to "Kiranzu Kiroza",
        "kiragualoisa" to "Aloisa Kiragu",
        "jassarah" to "Jas Sarah",
        "kinarajames" to "Kinara James",
        "jameskinara" to "James Kinara",
        "kinaramary" to "Mary Kinara",
        "marykinara" to "Mary Kinara",
        "john.doe" to "John Doe",
        "jane.doe" to "Jane Doe"
    )

    private val commonFirstNames = setOf(
        "mathias", "mathew", "mercy", "michael", "michelle", "mike", "miriam", "mohammed", "moses", "mwangi", "naomi", "nelson", "nicholas", "njeri", "noah", "obinna", "oliver", "oscar", "patrick", "patience", "paul", "peter", "philip", "phoebe", "precious", "prince", "princess", "rachel", "raymond", "rebecca", "richard", "robert", "rose", "ruth", "samson", "samuel", "sandra", "sarah", "stephen", "tabitha", "thomas", "timothy", "victor", "victoria", "vincent", "vivian", "william", "wilson", "winifred", "winnie",
        "kiragu", "mwangi", "maina", "kamau", "njoroge", "mburu", "wanjiku", "njeri", "nyambura", "wambui", "wangari", "wanjiru", "kinara", "james", "otieno", "onyango", "odhiambo", "okoth", "ogola", "atieno", "akoth", "anyango", "adambo", "musyoka", "mutua", "muli", "ndambuki", "kioko", "nzomo", "kivutha", "ngelu", "mwalimu", "ndeku", "juma", "bakari", "rashid", "omar", "said", "aziz", "fatuma", "asha", "zainab", "musa", "ibrahim",
        "abdul", "abdullah", "ahmed", "ali", "amin", "amir", "ashraf", "ayesha", "bilal", "faisal", "farrah", "hassan", "hussein", "ismail", "karim", "khalid", "laila", "mahmoud", "mariam", "mustafa", "nadia", "omar", "rashid", "samir", "youssef", "zayd",
        "chinedu", "efe", "emeka", "ngozi", "oluchi", "tunde", "yinka", "zainab", "kwame", "kofi", "ama", "yaw", "abena", "afia"
    )

    private val commonLastNames = setOf(
        "smith", "jones", "brown", "davis", "miller", "wilson", "moore",
        "taylor", "anderson", "thomas", "jackson", "white", "harris",
        "martin", "thompson", "garcia", "martinez", "robinson", "clark",
        "kiragu", "kiroza", "mwangi", "maina", "kamau", "otieno", "onyango",
        "odhiambo", "njeri", "wanjiku", "atieno", "mutua", "musyoka",
        "kinara", "james", "maina", "kamau", "njoroge", "mburu", "njoroge", "mbugua", "macharia", "kimani", "ngugi", "karau", "kinyanjui", "nyoro", "waititu", "sang", "ruto", "kosgei", "chepkwony", "kiprono", "kiplagat", "kipsang", "biwott", "lagat", "cheruiyot", "korir", "tanui", "tergat", "omwenga",
        "abdallah", "al-fayed", "al-ghazali", "abbas", "bakir", "daher", "el-baz", "fadel", "ghanim", "habib", "iad", "jaber", "khalil", "latif", "mansour", "nagi", "qadir", "rizk", "salem", "tahan", "uzair", "wahed", "yasin", "zaid"
    )

    fun formatFromEmail(email: String): String {
        if (email.isEmpty() || !email.contains("@")) return "Student"

        val emailPart = email.substringBefore("@").lowercase(Locale.getDefault())

        knownNameMappings[emailPart]?.let { return it }

        var cleanName = emailPart.replace("\\d+".toRegex(), "").trim()
        
        if (cleanName.length < 2) {
            cleanName = emailPart
        }

        val separators = listOf(".", "_", "-")
        for (separator in separators) {
            if (cleanName.contains(separator)) {
                val parts = cleanName.split(separator).filter { it.length >= 2 }
                if (parts.isNotEmpty()) {
                    val formatted = parts.joinToString(" ") { part ->
                        formatPart(part)
                    }
                    return formatted
                }
            }
        }

        val smartSplit = smartSplitName(cleanName)
        if (smartSplit != null) {
            return smartSplit
        }

        val genericSplit = splitPattern(cleanName)
        if (genericSplit != null) {
            return genericSplit
        }

        for (prefix in commonPrefixes) {
            if (emailPart == prefix || 
                emailPart.startsWith("$prefix.") || 
                emailPart.startsWith("${prefix}_")) {
                return formatPart(prefix)
            }
        }

        if (cleanName.length >= 2) {
            val capitalized = cleanName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            return capitalized
        }

        return "HomeHub Student"
    }

    fun formatFromGoogleAccount(
        displayName: String?,
        givenName: String?,
        familyName: String?,
        email: String?
    ): String {
        return when {
            !displayName.isNullOrEmpty() && displayName.trim().length >= 2 -> {
                displayName.trim()
            }
            !givenName.isNullOrEmpty() && !familyName.isNullOrEmpty() -> {
                "${givenName!!.trim()} ${familyName!!.trim()}"
            }
            !givenName.isNullOrEmpty() -> {
                givenName!!.trim()
            }
            !familyName.isNullOrEmpty() -> {
                familyName!!.trim()
            }
            !email.isNullOrEmpty() -> {
                formatFromEmail(email)
            }
            else -> "HomeHub Student"
        }
    }

    private fun smartSplitName(name: String): String? {
        if (name.length < 4) return null
        val nameLower = name.lowercase()
        for (lastName in commonLastNames) {
            if (nameLower.startsWith(lastName)) {
                val remaining = name.substring(lastName.length)
                if (remaining.length >= 2 && isPlausibleNamePart(remaining)) {
                    return "${formatPart(remaining)} ${formatPart(lastName)}"
                }
            }
        }
        for (firstName in commonFirstNames) {
            if (nameLower.startsWith(firstName)) {
                val remaining = name.substring(firstName.length)
                if (remaining.length >= 2 && isPlausibleNamePart(remaining)) {
                    return "${formatPart(firstName)} ${formatPart(remaining)}"
                }
            }
        }
        for (lastName in commonLastNames) {
            if (nameLower.endsWith(lastName)) {
                val remaining = name.substring(0, name.length - lastName.length)
                if (remaining.length >= 2 && isPlausibleNamePart(remaining)) {
                    return "${formatPart(remaining)} ${formatPart(lastName)}"
                }
            }
        }
        for (firstName in commonFirstNames) {
            if (nameLower.endsWith(firstName)) {
                val remaining = name.substring(0, name.length - firstName.length)
                if (remaining.length >= 2 && isPlausibleNamePart(remaining)) {
                    return "${formatPart(remaining)} ${formatPart(firstName)}"
                }
            }
        }
        return null
    }

    private fun formatPart(part: String): String {
        return if (part.isNotEmpty()) {
            part.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault())
                else it.toString()
            }
        } else {
            ""
        }
    }

    private fun splitPattern(name: String): String? {
        if (name.length < 4) return null
        val minLength = 2
        for (i in minLength..(name.length - minLength)) {
            val firstPart = name.substring(0, i)
            val secondPart = name.substring(i)
            if (isPlausibleNamePart(firstPart) && isPlausibleNamePart(secondPart)) {
                return "${formatPart(firstPart)} ${formatPart(secondPart)}"
            }
        }
        return null
    }

    private fun isPlausibleNamePart(part: String): Boolean {
        if (part.length < 2) return false
        val partLower = part.lowercase()
        if (commonFirstNames.contains(partLower) || commonLastNames.contains(partLower)) {
            return true
        }
        val hasVowel = part.any { it.lowercaseChar() in listOf('a', 'e', 'i', 'o', 'u') }
        val hasConsonant = part.any {
            it.lowercaseChar() in 'b'..'z' &&
                    it.lowercaseChar() !in listOf('a', 'e', 'i', 'o', 'u')
        }
        return hasVowel && hasConsonant
    }

    fun formatUsername(username: String): String {
        if (username.isEmpty()) return "HomeHub Student"
        if (username.contains(" ") && username.any { it.isUpperCase() }) return username // Already looks formatted
        return username
    }

    fun getRelativeTime(date: java.util.Date?): String {
        if (date == null) return ""
        val time = date.time
        val now = System.currentTimeMillis()
        val diff = now - time

        return when {
            diff < 60 * 1000 -> "Just now"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m ago"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h ago"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}d ago"
            else -> {
                val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                sdf.format(date)
            }
        }
    }
}
