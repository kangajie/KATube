package id.katube.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import id.katube.R
import id.katube.ui.theme.Gold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KATubeTopBar(
    canNavigateBack: Boolean,
    title: String? = null,
    isLoggedIn: Boolean = false,
    avatarUrl: String? = null,
    onNavigateBack: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    TopAppBar(
        modifier = Modifier.height(64.dp),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_close),
                    )
                }
            }
        },
        title = {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 2.dp),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_katube_logo),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Gold, fontWeight = FontWeight.Bold)) {
                                append("KA")
                            }
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append("Tube")
                            }
                        },
                        fontSize = 25.sp,
                        letterSpacing = (-0.6).sp,
                    )
                }
            }
        },
        actions = {
            if (!canNavigateBack) {
                IconButton(onClick = onNotificationClick) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "Notifikasi",
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.action_search),
                        modifier = Modifier.size(22.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onAccountClick),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoggedIn && !avatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Akun",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (isLoggedIn) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Gold),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "G",
                                color = MaterialTheme.colorScheme.background,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.AccountCircle,
                            contentDescription = "Akun",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }
    )
}
