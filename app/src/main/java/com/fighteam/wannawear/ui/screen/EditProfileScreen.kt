package com.fighteam.wannawear.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fighteam.wannawear.data.AppState
import com.fighteam.wannawear.data.remote.uploadPickedImage
import com.fighteam.wannawear.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun EditProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val originalName   = AppState.myProfile?.name ?: ""
    val originalAvatar = AppState.myProfile?.avatar ?: ""

    var nickname by remember { mutableStateOf(originalName) }
    var bio by remember { mutableStateOf("") }
    var originalBio by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf(originalAvatar) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // User 모델엔 bio가 없어서, 화면 진입 시 한 번 최신 프로필을 따로 받아와 채워넣는다
    LaunchedEffect(Unit) {
        try {
            val raw = AppState.fetchMyProfileRaw()
            val b = raw.bio ?: ""
            bio = b
            originalBio = b
        } catch (_: Exception) { /* 실패해도 빈 값으로 계속 진행 */ }
    }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingAvatar = true
            scope.launch {
                val url = uploadPickedImage(context, uri, "avatar")
                isUploadingAvatar = false
                if (url != null) {
                    avatarUrl = url
                } else {
                    snackbarHostState.showSnackbar("사진 업로드에 실패했어요. 다시 시도해주세요.")
                }
            }
        }
    }

    fun pickAvatar() {
        if (!isUploadingAvatar) {
            avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    val canSave = nickname.isNotBlank() && !isUploadingAvatar && !isSaving

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(BgPrimary)
                .imePadding()
        ) {
            // 앱바
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "뒤로", tint = TextPrimary)
                }
                Text(
                    "프로필 수정", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                Button(
                    onClick = {
                        isSaving = true
                        AppState.updateProfile(
                            nickname  = nickname.trim().takeIf { it.isNotBlank() && it != originalName },
                            bio       = bio.takeIf { it != originalBio },
                            avatarUrl = avatarUrl.takeIf { it.isNotBlank() && it != originalAvatar }
                        ) { success ->
                            isSaving = false
                            if (success) onBack()
                            else scope.launch { snackbarHostState.showSnackbar("저장에 실패했어요. 다시 시도해주세요.") }
                        }
                    },
                    enabled = canSave,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentYellow, contentColor = AccentYellowText,
                        disabledContainerColor = BgCardDark, disabledContentColor = TextTertiary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AccentYellowText
                        )
                    } else {
                        Text("저장", fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))

                // 아바타 + 카메라 배지
                Box {
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(BgCard)
                            .clickable { pickAvatar() }
                    ) {
                        if (avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = avatarUrl, contentDescription = null,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                            )
                        }
                        if (isUploadingAvatar) {
                            Box(
                                Modifier.fillMaxSize().background(Color(0x88000000)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = AccentYellow, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            }
                        }
                    }
                    Box(
                        Modifier
                            .size(30.dp)
                            .align(Alignment.BottomEnd)
                            .background(AccentYellow, CircleShape)
                            .clickable { pickAvatar() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📷", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("탭해서 프로필 사진 바꾸기", color = TextSecondary, fontSize = 11.sp)

                Spacer(Modifier.height(28.dp))

                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                    focusedBorderColor = AccentYellow, unfocusedBorderColor = BorderSubtle,
                    focusedLabelColor = AccentYellow, unfocusedLabelColor = TextSecondary,
                    cursorColor = AccentYellow
                )

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { if (it.length <= 50) nickname = it },
                    label = { Text("닉네임") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors
                )

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 200) bio = it },
                    label = { Text("자기소개") },
                    placeholder = { Text("나를 소개하는 한마디를 적어보세요", color = TextTertiary, fontSize = 12.sp) },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors
                )
                Text(
                    "${bio.length}/200", color = TextTertiary, fontSize = 10.sp, textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )

                Spacer(Modifier.height(32.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        ) { data ->
            Snackbar(
                snackbarData   = data,
                containerColor = BgCard,
                contentColor   = TextPrimary,
                shape          = RoundedCornerShape(12.dp)
            )
        }
    }
}
