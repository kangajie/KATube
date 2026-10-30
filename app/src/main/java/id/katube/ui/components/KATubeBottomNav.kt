package id.katube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import id.katube.ui.navigation.Screen
import id.katube.ui.navigation.bottomNavItems
import id.katube.ui.theme.Gold

@Composable
fun KATubeBottomNav(
    currentRoute: String?,
    onNavigateToRoute: (String) -> Unit,
    onReselectRoute: ((String) -> Unit)? = null,
    avatarUrl: String? = null,
    isLoggedIn: Boolean = false,
) {
    NavigationBar(
        modifier = Modifier.height(56.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        bottomNavItems.forEach { item ->
            val isSelected = currentRoute == item.screen.route

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        onNavigateToRoute(item.screen.route)
                    } else {
                        onReselectRoute?.invoke(item.screen.route)
                    }
                },
                icon = {
                    if (item.screen == Screen.Library && isLoggedIn) {
                        // Tampilkan Avatar Pengguna Akun Google di Tab 'Anda' seperti YouTube resmi
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(1.5.dp, Gold, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!avatarUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = stringResource(item.titleRes),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(22.dp).clip(CircleShape),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Gold),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "G",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.Black,
                                    )
                                }
                            }
                        }
                    } else if (item.selectedDrawableRes != null && item.unselectedDrawableRes != null) {
                        val resId = if (isSelected) item.selectedDrawableRes else item.unselectedDrawableRes
                        Icon(
                            painter = painterResource(id = resId),
                            contentDescription = stringResource(item.titleRes),
                            modifier = Modifier.size(24.dp),
                        )
                    } else if (item.selectedIcon != null && item.unselectedIcon != null) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = stringResource(item.titleRes),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                label = {
                    Text(
                        text = stringResource(item.titleRes),
                        fontSize = 10.sp,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Gold,
                    selectedTextColor = Gold,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    }
}
