package et.birrwise.app.domain

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

enum class TransactionSort {
    DATE_NEWEST,
    DATE_OLDEST,
    AMOUNT_LARGEST,
    AMOUNT_SMALLEST;

    val groupsByDate: Boolean get() = this == DATE_NEWEST || this == DATE_OLDEST
}

/** What the person is searching for and how the Transactions list is ordered. */
data class TransactionFilter(
    /** Matches the description, person or business, merchant, and notes. Ignores case. */
    val query: String = "",
    val accountId: String? = null,
    /** A category id, or [UNCATEGORIZED] for entries without a category. */
    val categoryId: String? = null,
    /** A transfer matches both of its sides. */
    val kind: EntryKind? = null,
    /** First day to include, in the phone's time zone. */
    val fromDate: LocalDate? = null,
    /** Last day to include, in the phone's time zone. */
    val toDate: LocalDate? = null,
    val reviewOnly: Boolean = false,
    val sort: TransactionSort = TransactionSort.DATE_NEWEST
) {
    /** How many filters are in effect. The sort order is not a filter. */
    val activeCount: Int
        get() = listOf(
            query.isNotBlank(),
            accountId != null,
            categoryId != null,
            kind != null,
            fromDate != null,
            toDate != null,
            reviewOnly
        ).count { it }

    val isActive: Boolean get() = activeCount > 0

    companion object {
        const val UNCATEGORIZED = "__none__"
    }
}

object TransactionFilters {

    fun apply(
        items: List<TransactionListItem>,
        filter: TransactionFilter,
        zone: ZoneId
    ): List<TransactionListItem> {
        val matching = items.filter { matches(it.transaction, filter, zone) }
        return when (filter.sort) {
            TransactionSort.DATE_NEWEST ->
                matching.sortedWith(
                    compareByDescending<TransactionListItem> { it.transaction.occurredAt }
                        .thenBy { it.transaction.id }
                )
            TransactionSort.DATE_OLDEST ->
                matching.sortedWith(
                    compareBy<TransactionListItem> { it.transaction.occurredAt }
                        .thenBy { it.transaction.id }
                )
            TransactionSort.AMOUNT_LARGEST ->
                matching.sortedWith(
                    compareByDescending<TransactionListItem> { it.transaction.amount.minorUnits }
                        .thenByDescending { it.transaction.occurredAt }
                        .thenBy { it.transaction.id }
                )
            TransactionSort.AMOUNT_SMALLEST ->
                matching.sortedWith(
                    compareBy<TransactionListItem> { it.transaction.amount.minorUnits }
                        .thenByDescending { it.transaction.occurredAt }
                        .thenBy { it.transaction.id }
                )
        }
    }

    private fun matches(transaction: Transaction, filter: TransactionFilter, zone: ZoneId): Boolean {
        if (filter.accountId != null && transaction.accountId != filter.accountId) return false

        val categoryId = filter.categoryId
        if (categoryId != null) {
            if (categoryId == TransactionFilter.UNCATEGORIZED) {
                if (transaction.categoryId != null) return false
            } else if (transaction.categoryId != categoryId) {
                return false
            }
        }

        if (filter.kind != null && !matchesKind(transaction.type, filter.kind)) return false
        if (filter.reviewOnly && transaction.status != TransactionStatus.PENDING_REVIEW) return false

        if (filter.fromDate != null || filter.toDate != null) {
            val day = transaction.occurredAt.atZone(zone).toLocalDate()
            if (filter.fromDate != null && day < filter.fromDate) return false
            if (filter.toDate != null && day > filter.toDate) return false
        }

        return matchesQuery(transaction, filter.query)
    }

    private fun matchesKind(type: TransactionType, kind: EntryKind): Boolean = when (kind) {
        EntryKind.INCOME -> type == TransactionType.INCOME
        EntryKind.EXPENSE -> type == TransactionType.EXPENSE
        EntryKind.FEE -> type == TransactionType.FEE
        EntryKind.REFUND -> type == TransactionType.REFUND
        EntryKind.TRANSFER -> type == TransactionType.TRANSFER_IN || type == TransactionType.TRANSFER_OUT
    }

    private fun matchesQuery(transaction: Transaction, query: String): Boolean {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return true
        return listOfNotNull(
            transaction.description,
            transaction.merchantName,
            transaction.counterparty,
            transaction.notes
        ).any { it.lowercase(Locale.ROOT).contains(needle) }
    }
}

/** A row in the Transactions list: a day heading, or an entry. */
sealed interface TransactionListRow {
    data class DateHeader(val date: LocalDate) : TransactionListRow
    data class Entry(val item: TransactionListItem) : TransactionListRow
}

object TransactionGroups {

    /** Adds a heading before each new day when the list is ordered by date. Amount order has no headings. */
    fun rows(
        items: List<TransactionListItem>,
        sort: TransactionSort,
        zone: ZoneId
    ): List<TransactionListRow> {
        if (!sort.groupsByDate) return items.map { TransactionListRow.Entry(it) }
        val rows = mutableListOf<TransactionListRow>()
        var currentDay: LocalDate? = null
        for (item in items) {
            val day = item.transaction.occurredAt.atZone(zone).toLocalDate()
            if (day != currentDay) {
                rows += TransactionListRow.DateHeader(day)
                currentDay = day
            }
            rows += TransactionListRow.Entry(item)
        }
        return rows
    }
}
