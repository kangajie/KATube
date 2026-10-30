package id.katube.core.util

import android.os.Build
import android.text.Html

object CommentUtils {

    /**
     * Membersihkan teks komentar dan deskripsi video dari segala tag HTML dan entitas kode sehingga menjadi teks polos yang rapi dan mudah dibaca.
     */
    @Suppress("DEPRECATION")
    fun cleanCommentText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""

        // 1. Normalisasi line breaks
        var text = raw
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("(?i)<p[^>]*>"), "")

        // 2. Parse dengan Html.fromHtml untuk menangani entities & struktur HTML standar
        text = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()
            } else {
                Html.fromHtml(text).toString()
            }
        } catch (_: Exception) {
            text
        }

        // 3. Bersihkan sisa tag HTML seperti <a href="...">, <span>, <div>, dll
        text = text.replace(Regex("<[^>]+>"), "")

        // 4. Bersihkan entitas HTML bernama (named entities) umum
        text = text
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#039;", "'")
            .replace("&#x27;", "'")
            .replace("&#x2F;", "/")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&copy;", "©")
            .replace("&reg;", "®")
            .replace("&hellip;", "…")
            .replace("&ndash;", "–")
            .replace("&mdash;", "—")
            .replace("&#8216;", "‘")
            .replace("&#8217;", "’")
            .replace("&#8220;", "“")
            .replace("&#8221;", "”")

        // 5. Decode entitas numerik desimal: &#1234;
        text = text.replace(Regex("&#(\\d+);")) { match ->
            try {
                match.groupValues[1].toInt().toChar().toString()
            } catch (_: Exception) {
                match.value
            }
        }

        // 6. Decode entitas numerik heksadesimal: &#x1a2b;
        text = text.replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
            try {
                match.groupValues[1].toInt(16).toChar().toString()
            } catch (_: Exception) {
                match.value
            }
        }

        // 7. Tangani unicode literal seperti \u0026 jika ada
        if (text.contains("\\u00") || text.contains("\\u0")) {
            text = text.replace(Regex("\\\\u([0-9a-fA-F]{4})")) { match ->
                try {
                    match.groupValues[1].toInt(16).toChar().toString()
                } catch (_: Exception) {
                    match.value
                }
            }
        }

        // 8. Putaran kedua untuk entitas bertingkat seperti &amp;amp; -> &amp; -> &
        text = text
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

        // 9. Rapikan jeda baris kosong berturut-turut (maksimal 2 baris baru)
        return text.replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    /**
     * Memastikan username/handle hanya memiliki tepat satu awalan '@'.
     * Menghilangkan kemungkinan double '@@'.
     */
    fun formatAuthorHandle(authorName: String?): String {
        val trimmed = authorName?.trim().orEmpty().trimStart('@')
        return if (trimmed.isNotEmpty()) "@$trimmed" else "@Pengguna"
    }
}
