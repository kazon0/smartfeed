package com.example.smartfeedandroid.ui.profile

import android.graphics.BitmapFactory
import android.content.Intent
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.data.remote.AuthUser
import com.example.smartfeedandroid.ui.common.JournalBlue
import com.example.smartfeedandroid.ui.common.JournalGreen
import com.example.smartfeedandroid.ui.common.JournalInk
import com.example.smartfeedandroid.ui.common.JournalInkLight
import com.example.smartfeedandroid.ui.common.JournalLine
import com.example.smartfeedandroid.ui.common.JournalPaper
import com.example.smartfeedandroid.ui.common.JournalPink
import com.example.smartfeedandroid.ui.common.JournalTerra
import com.example.smartfeedandroid.ui.common.JournalYellow
import com.example.smartfeedandroid.ui.theme.KalamFontFamily

@Composable
fun ProfileScreen(
    user: AuthUser,
    articleCount: Int,
    conversationCount: Int,
    isUpdatingProfile: Boolean,
    profileErrorMessage: String?,
    onUpdateProfile: (String, String) -> Unit,
    onClearProfileError: () -> Unit,
    onOpenArticles: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onNewChat: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var pendingDisplayName by remember { mutableStateOf<String?>(null) }
    var pendingBio by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val avatarStore = remember { ProfileAvatarStore(context.applicationContext) }
    var avatarUri by remember(user.id) { mutableStateOf(avatarStore.load(user.id)) }
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            avatarStore.save(user.id, it.toString())
            avatarUri = it.toString()
        }
    }

    LaunchedEffect(user.displayName, user.bio, isUpdatingProfile, pendingDisplayName, pendingBio) {
        val pendingName = pendingDisplayName
        val nextBio = pendingBio
        if (
            pendingName != null && nextBio != null && !isUpdatingProfile &&
            user.displayName == pendingName && user.bio == nextBio
        ) {
            showEditDialog = false
            pendingDisplayName = null
            pendingBio = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JournalPaper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .padding(bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.hello_traveler),
                color = JournalTerra,
                fontFamily = KalamFontFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.profile_home),
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.headlineMedium,
                color = JournalInk,
                fontWeight = FontWeight.ExtraBold
            )
        }

        ProfileHero(
            user = user,
            avatarUri = avatarUri,
            onEditProfile = { showEditDialog = true }
        )

        ProfilePanel(title = stringResource(R.string.account_information)) {
            AccountInfoRow(
                icon = Icons.Filled.Email,
                label = stringResource(R.string.email),
                value = user.email
            )
            AccountInfoRow(
                icon = Icons.Filled.Person,
                label = stringResource(R.string.joined_on),
                value = profileDate(user.createdAt)
            )
        }

        profileErrorMessage?.let {
            Text(
                text = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF6E5E2), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                color = MaterialTheme.colorScheme.error
            )
        }

        KnowledgeFootprint(
            articleCount = articleCount,
            conversationCount = conversationCount
        )

        ProfilePanel(title = stringResource(R.string.profile_shortcuts)) {
            ProfileActionRow(
                glyph = "▤",
                tint = JournalBlue,
                title = stringResource(R.string.profile_open_articles),
                onClick = onOpenArticles
            )
            HorizontalDivider(color = JournalLine.copy(alpha = 0.65f))
            ProfileActionRow(
                glyph = "◔",
                tint = JournalGreen,
                title = stringResource(R.string.profile_open_analysis),
                onClick = onOpenAnalysis
            )
            HorizontalDivider(color = JournalLine.copy(alpha = 0.65f))
            ProfileActionRow(
                glyph = "+",
                tint = JournalTerra,
                title = stringResource(R.string.profile_new_chat),
                onClick = onNewChat
            )
        }

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .width(226.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showLogoutDialog = true }
                    .drawBehind {
                        drawRoundRect(
                            color = JournalInkLight.copy(alpha = 0.78f),
                            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))
                            )
                        )
                    }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                JournalGlyph(text = "↪", tint = JournalTerra, fontSize = 18)
                Text(
                    text = stringResource(R.string.logout),
                    modifier = Modifier.padding(start = 8.dp),
                    color = JournalTerra,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showEditDialog) {
        EditProfileDialog(
            user = user,
            avatarUri = avatarUri,
            isSaving = isUpdatingProfile,
            errorMessage = profileErrorMessage,
            hasCustomAvatar = avatarUri != null,
            onChooseFromGallery = {
                avatarPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onUseInitial = {
                avatarStore.clear(user.id)
                avatarUri = null
            },
            onDismiss = {
                showEditDialog = false
                pendingDisplayName = null
                pendingBio = null
                onClearProfileError()
            },
            onSave = { displayName, bio ->
                pendingDisplayName = displayName.trim()
                pendingBio = bio.trim()
                onUpdateProfile(displayName, bio)
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.logout_confirmation_title)) },
            text = { Text(stringResource(R.string.logout_confirmation_message)) },
            confirmButton = {
                TextButton(onClick = onLogout) {
                    Text(stringResource(R.string.logout), color = JournalTerra)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = JournalPaper
        )
    }
}

@Composable
private fun ProfileAvatarImage(avatarUri: String?, fallbackInitial: String) {
    val context = LocalContext.current
    val bitmap = remember(avatarUri) {
        avatarUri?.let { value -> decodeAvatarBitmap(context, Uri.parse(value)) }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = stringResource(R.string.choose_avatar),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Text(
            text = fallbackInitial,
            color = JournalInk,
            fontFamily = KalamFontFamily,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineLarge
        )
    }
}

private fun decodeAvatarBitmap(context: Context, uri: Uri): ImageBitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 512) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
        }
    }.getOrNull()
}

@Composable
private fun ProfileHero(
    user: AuthUser,
    avatarUri: String?,
    onEditProfile: () -> Unit
) {
    val displayName = user.displayName.ifBlank { stringResource(R.string.smartfeed_user) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-30).dp, y = (-22).dp)
                .rotate(-6f)
                .size(width = 42.dp, height = 52.dp)
                .background(JournalBlue.copy(alpha = 0.48f), RoundedCornerShape(5.dp))
                .border(1.dp, JournalBlue.copy(alpha = 0.65f), RoundedCornerShape(5.dp))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .rotate(-0.3f)
                .shadow(2.dp, RoundedCornerShape(24.dp), clip = false)
                .background(Color(0xFFFCF5F6), RoundedCornerShape(24.dp))
                .border(1.dp, JournalPink.copy(alpha = 0.48f), RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(Color.White.copy(alpha = 0.82f), CircleShape)
                        .drawBehind {
                            drawCircle(
                                color = JournalInkLight.copy(alpha = 0.34f),
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f))
                                )
                            )
                        }
                        .clip(CircleShape)
                        .clickable(onClick = onEditProfile),
                    contentAlignment = Alignment.Center
                ) {
                    ProfileAvatarImage(
                        avatarUri = avatarUri,
                        fallbackInitial = profileInitial(displayName, user.email)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .size(18.dp)
                        .shadow(2.dp, CircleShape, clip = false)
                        .background(JournalInk, CircleShape)
                        .clip(CircleShape)
                        .clickable(onClick = onEditProfile),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.edit_profile),
                        tint = JournalPaper,
                        modifier = Modifier.size(9.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 17.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = displayName,
                    color = JournalInk,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = user.bio.ifBlank { stringResource(R.string.profile_bio_empty) },
                    color = JournalInkLight,
                    fontFamily = KalamFontFamily,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun KnowledgeFootprint(
    articleCount: Int,
    conversationCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ProfileStatCard(
            value = articleCount,
            label = stringResource(R.string.profile_articles),
            glyph = "▤",
            tint = JournalYellow,
            modifier = Modifier.weight(1f),
            rotation = -0.5f
        )
        ProfileStatCard(
            value = conversationCount,
            label = stringResource(R.string.profile_conversations),
            glyph = "◔",
            tint = JournalBlue,
            modifier = Modifier.weight(1f),
            rotation = 0.5f
        )
    }
}

@Composable
private fun ProfileStatCard(
    value: Int,
    label: String,
    glyph: String,
    tint: Color,
    modifier: Modifier = Modifier,
    rotation: Float
) {
    Box(modifier = modifier.padding(top = 7.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .rotate(rotation)
                .shadow(4.dp, RoundedCornerShape(20.dp), clip = false)
                .background(Color.White, RoundedCornerShape(20.dp))
                .border(1.dp, JournalLine, RoundedCornerShape(20.dp))
                .padding(horizontal = 17.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                JournalGlyph(text = glyph, tint = tint, fontSize = 17)
                Text(
                    text = label,
                    color = JournalInkLight,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value.toString(),
                    color = JournalInk,
                    fontFamily = KalamFontFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = if (label == stringResource(R.string.profile_articles)) " 篇" else " 个",
                    modifier = Modifier.padding(bottom = 5.dp),
                    color = JournalInkLight,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 20.dp, y = (-7).dp)
                .rotate(-2f)
                .size(width = 46.dp, height = 15.dp)
                .background(tint.copy(alpha = 0.62f), RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun ProfilePanel(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.78f), RoundedCornerShape(20.dp))
            .border(1.dp, JournalLine, RoundedCornerShape(20.dp))
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            color = JournalInk,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold
        )
        content()
    }
}

@Composable
private fun ProfileActionRow(glyph: String, tint: Color, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(tint.copy(alpha = 0.28f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            JournalGlyph(text = glyph, tint = tint, fontSize = 18)
        }
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 11.dp),
            color = JournalInk,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "›",
            color = JournalInkLight,
            fontFamily = KalamFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
    }
}

@Composable
private fun AccountInfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = JournalBlue, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.padding(start = 11.dp)) {
            Text(text = label, color = JournalInkLight, style = MaterialTheme.typography.labelSmall)
            Text(
                text = value,
                color = JournalInk,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EditProfileDialog(
    user: AuthUser,
    avatarUri: String?,
    isSaving: Boolean,
    errorMessage: String?,
    hasCustomAvatar: Boolean,
    onChooseFromGallery: () -> Unit,
    onUseInitial: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var displayName by remember(user.displayName) { mutableStateOf(user.displayName) }
    var bio by remember(user.bio) { mutableStateOf(user.bio) }
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(R.string.edit_profile)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(76.dp)
                        .background(Color.White, CircleShape)
                        .border(2.dp, JournalLine, CircleShape)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    ProfileAvatarImage(
                        avatarUri = avatarUri,
                        fallbackInitial = profileInitial(displayName, user.email)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onChooseFromGallery, enabled = !isSaving) {
                        JournalGlyph(text = "▧", tint = MaterialTheme.colorScheme.primary, fontSize = 16)
                        Text(
                            text = stringResource(R.string.choose_from_gallery),
                            modifier = Modifier.padding(start = 5.dp)
                        )
                    }
                    if (hasCustomAvatar) {
                        TextButton(onClick = onUseInitial, enabled = !isSaving) {
                            Text(stringResource(R.string.use_initial_avatar))
                        }
                    }
                }
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= 120) displayName = it },
                    label = { Text(stringResource(R.string.display_name)) },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 200) bio = it },
                    label = { Text(stringResource(R.string.profile_bio)) },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(displayName, bio) },
                enabled = displayName.isNotBlank() && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.save_profile))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.cancel))
            }
        },
        containerColor = JournalPaper
    )
}

@Composable
private fun JournalGlyph(text: String, tint: Color, fontSize: Int) {
    Text(
        text = text,
        color = tint,
        fontFamily = KalamFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize.sp,
        lineHeight = fontSize.sp
    )
}

internal fun profileInitial(displayName: String, email: String): String {
    return displayName.trim().firstOrNull()?.uppercase()
        ?: email.trim().firstOrNull()?.uppercase()
        ?: "S"
}

internal fun profileDate(createdAt: String): String {
    return createdAt.trim().take(10).ifBlank { "-" }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    ProfileScreen(
        user = AuthUser(
            id = "user-1",
            email = "reader@example.com",
            displayName = "Journal Reader",
            bio = "保持好奇，持续收集灵感碎片。",
            createdAt = "2026-06-22T00:00:00Z"
        ),
        articleCount = 12,
        conversationCount = 8,
        isUpdatingProfile = false,
        profileErrorMessage = null,
        onUpdateProfile = { _, _ -> },
        onClearProfileError = {},
        onOpenArticles = {},
        onOpenAnalysis = {},
        onNewChat = {},
        onLogout = {}
    )
}
