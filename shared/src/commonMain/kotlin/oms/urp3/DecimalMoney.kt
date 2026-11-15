package oms.urp3

/** Decimal HALF_UP rounding to minor units, shared by JVM/JS/WASM.
 * Interpret the input's decimal representation, multiply/divide exactly, then round once.
 * Missing/invalid rates and Long overflow are unavailable, never zero or a saturated Long.
 */
fun decimalMoneyCents(amount: Double, rate: Double = 1.0, divideByRate: Boolean = false): Long? {
    if (!amount.isFinite() || !rate.isFinite() || rate <= 0.0) return null
    val value = decimalParts(amount)
    if (value.digits == "0") return 0L
    val conversion = decimalParts(rate)
    val magnitude = if (divideByRate) {
        val shift = value.power - conversion.power + 2
        val numerator = value.digits + "0".repeat(shift.coerceAtLeast(0))
        val denominator = conversion.digits + "0".repeat((-shift).coerceAtLeast(0))
        divideRounded(numerator, denominator)
    } else {
        val product = multiplyDigits(value.digits, conversion.digits)
        val shift = value.power + conversion.power + 2
        if (shift >= 0) {
            if (product.length + shift > 19) return null
            product + "0".repeat(shift)
        } else {
            val boundary = product.length + shift
            val whole = if (boundary > 0) product.take(boundary) else "0"
            if (boundary >= 0 && product[boundary] >= '5') incrementDigits(whole) else whole
        }
    }
    return (if (value.negative && magnitude != "0") "-$magnitude" else magnitude).toLongOrNull()
}

private data class DecimalParts(val negative: Boolean, val digits: String, val power: Int)

private fun decimalParts(value: Double): DecimalParts {
    val text = value.toString().lowercase()
    val mantissa = text.substringBefore('e').removePrefix("-")
    val exponent = if ('e' in text) text.substringAfter('e').toInt() else 0
    val decimals = if ('.' in mantissa) mantissa.length - mantissa.indexOf('.') - 1 else 0
    return DecimalParts(value < 0, mantissa.replace(".", "").trimStart('0').ifEmpty { "0" }, exponent - decimals)
}

private fun multiplyDigits(left: String, right: String): String {
    val result = IntArray(left.length + right.length)
    for (i in left.indices.reversed()) for (j in right.indices.reversed()) {
        val product = (left[i] - '0') * (right[j] - '0') + result[i + j + 1]
        result[i + j + 1] = product % 10
        result[i + j] += product / 10
    }
    return result.joinToString("").trimStart('0').ifEmpty { "0" }
}

private fun incrementDigits(value: String): String {
    val result = value.toCharArray()
    for (i in result.indices.reversed()) {
        if (result[i] != '9') { result[i] = result[i] + 1; return result.concatToString() }
        result[i] = '0'
    }
    return "1" + result.concatToString()
}

private fun compareDigits(left: String, right: String): Int =
    if (left.length != right.length) left.length.compareTo(right.length) else left.compareTo(right)

/** Unsigned subtraction, only called with left >= right. */
private fun subtractDigits(left: String, right: String): String {
    val result = left.toCharArray()
    var borrow = 0
    for (offset in left.indices) {
        val i = left.lastIndex - offset
        val j = right.lastIndex - offset
        var digit = left[i] - '0' - borrow - if (j >= 0) right[j] - '0' else 0
        borrow = if (digit < 0) 1 else 0
        if (digit < 0) digit += 10
        result[i] = '0' + digit
    }
    return result.concatToString().trimStart('0').ifEmpty { "0" }
}

private fun divideRounded(numerator: String, denominator: String): String {
    var remainder = "0"
    val quotient = StringBuilder()
    for (digit in numerator) {
        remainder = (remainder + digit).trimStart('0').ifEmpty { "0" }
        var next = 0
        while (compareDigits(remainder, denominator) >= 0) {
            remainder = subtractDigits(remainder, denominator)
            next++
        }
        quotient.append('0' + next)
    }
    val whole = quotient.toString().trimStart('0').ifEmpty { "0" }
    return if (compareDigits(multiplyDigits(remainder, "2"), denominator) >= 0) incrementDigits(whole) else whole
}
