package et.birrwise.app.domain

import java.math.BigDecimal
import java.math.BigInteger
import java.util.Currency

/**
 * An exact amount of money, stored as a whole number of minor units (for ETB, cents).
 * Binary floating point is never used. Arithmetic fails loudly on overflow instead of wrapping.
 */
data class Money(val minorUnits: Long, val currencyCode: String) {

    init {
        require(isSupportedCurrency(currencyCode)) { "Unsupported currency code: $currencyCode" }
    }

    /** Number of decimal places for this currency (2 for ETB, 0 for JPY). */
    val fractionDigits: Int
        get() = Currency.getInstance(currencyCode).defaultFractionDigits

    val isZero: Boolean get() = minorUnits == 0L
    val isNegative: Boolean get() = minorUnits < 0L
    val isPositive: Boolean get() = minorUnits > 0L

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.addExact(minorUnits, other.minorUnits), currencyCode)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.subtractExact(minorUnits, other.minorUnits), currencyCode)
    }

    operator fun unaryMinus(): Money = Money(Math.negateExact(minorUnits), currencyCode)

    private fun requireSameCurrency(other: Money) {
        require(currencyCode == other.currencyCode) {
            "Currency mismatch: $currencyCode vs ${other.currencyCode}"
        }
    }

    companion object {
        fun zero(currencyCode: String): Money = Money(0L, currencyCode)

        private fun isSupportedCurrency(code: String): Boolean = try {
            Currency.getInstance(code).defaultFractionDigits >= 0
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}

/** Turns a [Money] into text such as "1,234.56 ETB". Output does not depend on the phone's locale. */
object MoneyFormatter {

    fun format(money: Money, includeCurrency: Boolean = true): String {
        val unscaled = BigInteger.valueOf(money.minorUnits).abs()
        val plain = BigDecimal(unscaled, money.fractionDigits).toPlainString()
        val dot = plain.indexOf('.')
        val integerPart = if (dot >= 0) plain.substring(0, dot) else plain
        val fractionPart = if (dot >= 0) plain.substring(dot) else ""
        val grouped = integerPart.reversed().chunked(3).joinToString(",").reversed()
        val sign = if (money.minorUnits < 0L) "-" else ""
        val body = sign + grouped + fractionPart
        return if (includeCurrency) "$body ${money.currencyCode}" else body
    }
}

/**
 * Reads an amount typed by a person. The dot is the decimal separator and commas are accepted only
 * as thousands separators in groups of three, so "12,50" is rejected instead of becoming 1,250.
 * Negative numbers, exponents, and amounts needing rounding are rejected (returns null).
 */
object MoneyParser {

    private val amountPattern = Regex("""(\d+|\d{1,3}(,\d{3})+)(\.\d+)?""")

    fun parse(text: String, currencyCode: String): Money? {
        val trimmed = text.trim()
        if (!amountPattern.matches(trimmed)) return null
        val digits = Currency.getInstance(currencyCode).defaultFractionDigits
        val decimal = BigDecimal(trimmed.replace(",", ""))
        if (decimal.scale() > digits) return null
        val unscaled = decimal.setScale(digits).unscaledValue()
        if (unscaled.bitLength() > 63) return null
        return Money(unscaled.toLong(), currencyCode)
    }
}
