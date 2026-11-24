package com.guardian.adblock

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Keeps only valid daily aggregate keys inside the configured rolling retention window. */
internal object PremiumReportRetentionPolicy {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun retainDates(
        storedDates: Set<String>,
        currentDate: LocalDate,
        retentionDays: Int
    ): Set<String> {
        require(retentionDays > 0) { "보관 기간은 1일 이상이어야 합니다." }
        val oldestRetainedDate = currentDate.minusDays((retentionDays - 1).toLong())
        return storedDates.filterTo(linkedSetOf()) { raw ->
            val date = runCatching { LocalDate.parse(raw, formatter) }.getOrNull()
            date != null && !date.isBefore(oldestRetainedDate) && !date.isAfter(currentDate)
        }
    }
}
