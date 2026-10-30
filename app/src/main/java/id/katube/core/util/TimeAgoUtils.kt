package id.katube.core.util

import org.schabi.newpipe.extractor.localization.DateWrapper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.regex.Pattern

/**
 * Utilitas pemformat waktu relatif gaya YouTube dalam bahasa Indonesia:
 * - Baru saja / X detik yang lalu
 * - X menit yang lalu
 * - X jam yang lalu
 * - X hari yang lalu
 * - X minggu yang lalu
 * - X bulan yang lalu
 * - X tahun yang lalu
 */
object TimeAgoUtils {

    fun format(dateWrapper: DateWrapper?, rawDateText: String? = null): String {
        // 1. Coba dari DateWrapper (paling akurat)
        if (dateWrapper != null) {
            try {
                val instant = dateWrapper.instant
                val formatted = formatFromInstant(instant)
                if (formatted.isNotEmpty()) return formatted
            } catch (_: Exception) {
            }
        }

        // 2. Parse dari rawDateText
        if (!rawDateText.isNullOrBlank()) {
            val trimmed = rawDateText.trim()
            if (trimmed.equals("LIVE", ignoreCase = true) || trimmed.equals("Sedang Live", ignoreCase = true)) {
                return ""
            }
            val formatted = formatFromRawText(trimmed)
            if (formatted.isNotEmpty()) return formatted
        }

        val fallback = rawDateText.orEmpty().trim()
        if (fallback.equals("LIVE", ignoreCase = true) || fallback.equals("Sedang Live", ignoreCase = true)) {
            return ""
        }
        return fallback
    }

    fun formatFromInstant(instant: Instant): String {
        val now = Instant.now()
        val diffSeconds = ChronoUnit.SECONDS.between(instant, now).coerceAtLeast(0)

        return when {
            diffSeconds < 60 -> "Baru saja"
            diffSeconds < 3600 -> {
                val minutes = (diffSeconds / 60).coerceAtLeast(1)
                "$minutes menit yang lalu"
            }
            diffSeconds < 86400 -> {
                val hours = (diffSeconds / 3600).coerceAtLeast(1)
                "$hours jam yang lalu"
            }
            diffSeconds < 7 * 86400 -> {
                val days = (diffSeconds / 86400).coerceAtLeast(1)
                "$days hari yang lalu"
            }
            diffSeconds < 30 * 86400 -> {
                val weeks = (diffSeconds / (7 * 86400)).coerceAtLeast(1)
                "$weeks minggu yang lalu"
            }
            diffSeconds < 365 * 86400 -> {
                val months = (diffSeconds / (30 * 86400)).coerceAtLeast(1)
                "$months bulan yang lalu"
            }
            else -> {
                val years = (diffSeconds / (365 * 86400)).coerceAtLeast(1)
                "$years tahun yang lalu"
            }
        }
    }

    private val ENGLISH_TIME_PATTERN = Pattern.compile(
        """(?i)(?:streamed\s+(?:live\s+)?|premiered\s+)?(\d+)\s+(second|sec|minute|min|hour|hr|day|week|month|year)s?\s+ago"""
    )
    private val INDO_TIME_PATTERN = Pattern.compile(
        """(?i)(?:ditayangkan\s+perdana\s+|streaming\s+langsung\s+)?(\d+)\s+(detik|menit|jam|hari|minggu|bulan|tahun)\s*(?:yang\s+)?lalu"""
    )

    fun formatFromRawText(text: String): String {
        var trimmed = text.trim()
        if (trimmed.isEmpty()) return ""

        // Bersihkan awalan siaran atau penayangan perdana YouTube
        val lower = trimmed.lowercase()
        if (lower.startsWith("streamed live on ")) {
            trimmed = trimmed.substring(17).trim()
        } else if (lower.startsWith("streamed on ")) {
            trimmed = trimmed.substring(12).trim()
        } else if (lower.startsWith("premiered on ")) {
            trimmed = trimmed.substring(13).trim()
        } else if (lower.startsWith("ditayangkan perdana pada ")) {
            trimmed = trimmed.substring(25).trim()
        }

        // Pola bahasa Inggris
        val engMatcher = ENGLISH_TIME_PATTERN.matcher(trimmed)
        if (engMatcher.find()) {
            val count = engMatcher.group(1) ?: "1"
            val unit = engMatcher.group(2)?.lowercase() ?: ""
            return when {
                unit.startsWith("sec") -> "Baru saja"
                unit.startsWith("min") -> "$count menit yang lalu"
                unit.startsWith("hour") || unit.startsWith("hr") -> "$count jam yang lalu"
                unit.startsWith("day") -> "$count hari yang lalu"
                unit.startsWith("week") -> "$count minggu yang lalu"
                unit.startsWith("month") -> "$count bulan yang lalu"
                unit.startsWith("year") -> "$count tahun yang lalu"
                else -> "$count $unit yang lalu"
            }
        }

        // Pola bahasa Indonesia
        val indoMatcher = INDO_TIME_PATTERN.matcher(trimmed)
        if (indoMatcher.find()) {
            val count = indoMatcher.group(1) ?: "1"
            val unit = indoMatcher.group(2)?.lowercase() ?: ""
            return "$count $unit yang lalu"
        }

        if (trimmed.equals("just now", ignoreCase = true) || trimmed.equals("baru saja", ignoreCase = true)) {
            return "Baru saja"
        }

        // Format tanggal standar ISO
        try {
            val parsedDate = LocalDate.parse(trimmed)
            val instant = parsedDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
            return formatFromInstant(instant)
        } catch (_: Exception) {
        }

        // Format tanggal umum lainnya
        val formatters = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.US),
            DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.US),
            DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale("id", "ID")),
            DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale("id", "ID")),
            DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US),
            DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.US)
        )
        for (formatter in formatters) {
            try {
                val parsed = LocalDate.parse(trimmed, formatter)
                val instant = parsed.atStartOfDay(ZoneId.systemDefault()).toInstant()
                return formatFromInstant(instant)
            } catch (_: Exception) {
            }
        }

        return trimmed
    }
}
