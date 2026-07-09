package com.fighteam.wannawear.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fighteam.wannawear.data.AppState
import com.fighteam.wannawear.data.model.*
import com.fighteam.wannawear.data.remote.RetrofitClient
import com.fighteam.wannawear.ui.theme.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddItemScreen(onBack: () -> Unit) {
    var photoUrl by remember { mutableStateOf("") }        // 대표 사진 URL (POST /api/uploads 결과)
    var wearingUrl by remember { mutableStateOf("") }      // 실착 샷 URL
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ClothingCategory?>(null) }
    var size by remember { mutableStateOf("") }
    var heightFit by remember { mutableStateOf("") }
    var selectedCondition by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(listOf<String>()) }

    // ── 실제 사진 업로드 (갤러리 선택 → POST /api/uploads) ──────────────
    var isUploadingPhoto by remember { mutableStateOf(false) }
    var isUploadingWearing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    suspend fun uploadPickedImage(uri: Uri, purpose: String): String? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val ext = when {
                mime.contains("png")  -> "png"
                mime.contains("webp") -> "webp"
                mime.contains("gif")  -> "gif"
                else                  -> "jpg"
            }
            val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", "upload.$ext", body)
            val purposeBody = purpose.toRequestBody("text/plain".toMediaTypeOrNull())
            val res = RetrofitClient.api.uploadFile(part, purposeBody)
            if (res.success) res.data?.fileUrl else null
        } catch (e: Exception) {
            null
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isUploadingPhoto = true
            scope.launch {
                val url = uploadPickedImage(uri, "item_image")
                isUploadingPhoto = false
                if (url != null) photoUrl = url
                else scope.launch { snackbarHostState.showSnackbar("사진 업로드에 실패했어요. 다시 시도해주세요.") }
            }
        }
    }
    val wearingPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isUploadingWearing = true
            scope.launch {
                val url = uploadPickedImage(uri, "wearing_image")
                isUploadingWearing = false
                if (url != null) wearingUrl = url
                else scope.launch { snackbarHostState.showSnackbar("사진 업로드에 실패했어요. 다시 시도해주세요.") }
            }
        }
    }

    val conditions = listOf("새상품", "거의 새것", "양호", "보통", "사용감 있음")
    val canSave = name.isNotBlank() && brand.isNotBlank() && size.isNotBlank() &&
        selectedCondition.isNotBlank() && photoUrl.isNotBlank() &&
        !isUploadingPhoto && !isUploadingWearing

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .imePadding()
    ) {
        // 앱바
        Row(
            Modifier
                .fillMaxWidth()
                .background(BgPrimary)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "뒤로", tint = TextPrimary)
            }
            Text("옷 추가하기", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f).padding(start = 4.dp))
            Button(
                onClick = {
                    if (canSave) {
                        val newItem = ClothingItem(
                            id = 0, // 서버가 실제 id를 부여함 (요청 바디엔 안 쓰임)
                            image = photoUrl,
                            wearingImage = wearingUrl,
                            name = name,
                            brand = brand,
                            size = size,
                            heightFit = heightFit,
                            condition = selectedCondition,
                            category = selectedCategory ?: ClothingCategory.OTHER,
                            description = description,
                            user = AppState.myProfile ?: User(0, "", 0, ""),
                            tags = tags,
                            isListed = true
                        )
                        AppState.addMyItem(newItem) { success ->
                            if (success) onBack()
                        }
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
                Text("등록", fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {

            // ── 사진 업로드 영역 ──────────────────────────────────────

            SectionLabel("사진")

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // 대표 사진
                PhotoPickerBox(
                    label = "대표 사진 *",
                    url = photoUrl,
                    isUploading = isUploadingPhoto,
                    modifier = Modifier.weight(1f).height(160.dp),
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
                // 실착 샷
                PhotoPickerBox(
                    label = "실착 샷 (선택)",
                    url = wearingUrl,
                    isUploading = isUploadingWearing,
                    modifier = Modifier.weight(1f).height(160.dp),
                    onClick = {
                        wearingPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── 기본 정보 ──────────────────────────────────────────────

            SectionLabel("기본 정보")

            WwTextField(value = name, onValueChange = { name = it }, placeholder = "옷 이름 *  ex) 빈티지 레더 재킷")
            Spacer(Modifier.height(8.dp))
            WwTextField(value = brand, onValueChange = { brand = it }, placeholder = "브랜드 *  ex) Zara, 무신사 스탠다드")

            Spacer(Modifier.height(16.dp))

            // ── 카테고리 ──────────────────────────────────────────────

            SectionLabel("카테고리")

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClothingCategory.values().take(4).forEach { cat ->
                    ChipButton(
                        label = cat.label,
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClothingCategory.values().drop(4).forEach { cat ->
                    ChipButton(
                        label = cat.label,
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 빈 공간 채우기
                repeat(4 - ClothingCategory.values().drop(4).size) {
                    Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── 사이즈 / 권장 키 ──────────────────────────────────────

            SectionLabel("사이즈 & 권장 키")

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WwTextField(
                    value = size,
                    onValueChange = { size = it },
                    placeholder = "사이즈 *  ex) S / 95 / 28",
                    modifier = Modifier.weight(1f)
                )
                WwTextField(
                    value = heightFit,
                    onValueChange = { heightFit = it },
                    placeholder = "권장 키  ex) 165~175cm",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── 상태 ──────────────────────────────────────────────────

            SectionLabel("상태")

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                conditions.forEach { cond ->
                    ChipButton(
                        label = cond,
                        selected = selectedCondition == cond,
                        onClick = { selectedCondition = cond },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── 설명 / 하자 ───────────────────────────────────────────

            SectionLabel("설명 (하자, 특이사항)")

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text("ex) 오른쪽 소매 실밥 약간 풀림, 착용감 좋음", color = TextSecondary, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                    focusedBorderColor = AccentYellow, unfocusedBorderColor = BorderSubtle
                ),
                maxLines = 4
            )

            Spacer(Modifier.height(16.dp))

            // ── 태그 ──────────────────────────────────────────────────

            SectionLabel("태그")

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WwTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = "태그 입력  ex) 빈티지",
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val t = tagInput.trim()
                        if (t.isNotEmpty() && !tags.contains(t)) {
                            tags = tags + t
                            tagInput = ""
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BgCard, contentColor = TextPrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("추가", fontSize = 13.sp)
                }
            }

            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                // #3: FlowRow로 교체 — 태그가 많아도 자동 줄바꿈
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement   = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEach { tag ->
                        Row(
                            Modifier
                                .background(BgCard, RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("#$tag", color = TextPrimary, fontSize = 11.sp)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.Close, contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(12.dp).clickable { tags = tags - tag })
                        }
                    }
                }
            }

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
    } // close 최상단 Box
}

// ── 공통 컴포넌트 ─────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun WwTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = TextSecondary, fontSize = 12.sp) },
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
            focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
            focusedBorderColor = AccentYellow, unfocusedBorderColor = BorderSubtle
        )
    )
}

@Composable
private fun ChipButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AccentYellow else BgCard)
            .border(1.dp, if (selected) AccentYellow else BorderSubtle, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) AccentYellowText else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun PhotoPickerBox(
    label: String,
    url: String,
    modifier: Modifier = Modifier,
    isUploading: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .clickable(enabled = !isUploading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNotBlank()) {
            coil.compose.AsyncImage(
                model = url,
                contentDescription = label,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 재선택 힌트
            Box(Modifier.fillMaxSize().background(Color(0x33000000)), contentAlignment = Alignment.Center) {
                if (!isUploading) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                Text(label, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (isUploading) {
            Box(Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentYellow, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            }
        }
    }
}
