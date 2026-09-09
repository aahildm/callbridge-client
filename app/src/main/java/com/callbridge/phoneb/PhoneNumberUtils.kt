package com.callbridge.phoneb

/** Normalizes phone numbers so "+923014543035" and "03014543035" group as the same contact. */
object PhoneNumberUtils {

    /** Strips formatting and normalizes country code so numbers compare equal. */
    fun normalize(number: String): String {
        var digits = number.filter { it.isDigit() || it == '+' }

        // Pakistani numbers: convert +92 prefix to leading 0 for consistent comparison
        digits = when {
            digits.startsWith("+92") -> "0" + digits.removePrefix("+92")
            digits.startsWith("0092") -> "0" + digits.removePrefix("0092")
            digits.startsWith("92") && digits.length == 12 -> "0" + digits.removePrefix("92")
            else -> digits
        }
        return digits
    }

    /** True if two numbers refer to the same contact after normalization. */
    fun sameNumber(a: String, b: String): Boolean = normalize(a) == normalize(b)
}
