package id.katube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.katube.ui.theme.Gold

@Composable
fun ChipRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        items(categories) { category ->
            val isSelected = category == selectedCategory

            val backgroundColor = if (isSelected) {
                Gold
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }

            val textColor = if (isSelected) {
                MaterialTheme.colorScheme.background
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(backgroundColor)
                    .clickable { onCategorySelected(category) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = category,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}
