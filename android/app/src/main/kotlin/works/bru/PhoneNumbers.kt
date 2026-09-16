package works.bru

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager

object PhoneNumbers {
    fun simCountryIso(context: Context): String? = context
        .getSystemService(TelephonyManager::class.java)
        ?.simCountryIso
        ?.takeIf { it.isNotBlank() }
        ?.uppercase()

    fun normalizeE164(raw: String?, countryIso: String?): String? {
        if (raw.isNullOrBlank()) return raw
        val trimmed = raw.trim()
        if (trimmed.any { it.isLetter() }) return trimmed

        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return trimmed
        return when {
            trimmed.startsWith("+") -> "+$digits"
            digits.startsWith("00") -> "+${digits.drop(2)}"
            countryIso == null -> trimmed
            else -> PhoneNumberUtils.formatNumberToE164(trimmed, countryIso) ?: trimmed
        }
    }

    fun directionFromType(type: Int): String = if (type == 1) "in" else "out"
}
