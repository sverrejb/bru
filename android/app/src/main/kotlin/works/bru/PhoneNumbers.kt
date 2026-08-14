package works.bru

object PhoneNumbers {
    //TODO: Make this work for all cases, not just norwegian numbers.
    private const val DEFAULT_CC = "+47"

    fun normalizeE164(raw: String?): String? {
        if (raw.isNullOrBlank()) return raw
        val trimmed = raw.trim()
        if (trimmed.any { it.isLetter() }) return trimmed

        val plus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return trimmed
        return when {
            plus -> "+$digits"
            digits.startsWith("00") -> "+${digits.drop(2)}"
            else -> "$DEFAULT_CC$digits"
        }
    }

    fun directionFromType(type: Int): String = if (type == 1) "in" else "out"
}
