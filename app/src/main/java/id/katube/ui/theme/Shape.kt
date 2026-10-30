package id.katube.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Bentuk (Shapes) KATube sesuai DESIGN.md.
 * - Thumbnail: 12dp pada kartu beranda
 * - Badge durasi: 4dp
 * - Chip kategori: 8dp
 * - Tombol aksi/langganan: full rounded (pil)
 */
val KATubeShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
