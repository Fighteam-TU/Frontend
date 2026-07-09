package com.fighteam.wannawear.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fighteam.wannawear.data.remote.RetrofitClient
import com.fighteam.wannawear.data.remote.TokenManager
import com.fighteam.wannawear.data.remote.dto.ApiException
import com.fighteam.wannawear.data.remote.dto.LoginRequest
import com.fighteam.wannawear.data.remote.dto.RegisterRequest
import com.fighteam.wannawear.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var isLoginMode by remember { mutableStateOf(true) }
    var email       by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var nickname    by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // ✅ 유효성 검사: 로그인은 이메일+비밀번호, 회원가입은 닉네임까지
    val isEmailValid    = email.contains("@") && email.contains(".")
    val isPasswordValid = password.length >= 6
    val canProceed = !isSubmitting && if (isLoginMode) {
        isEmailValid && isPasswordValid
    } else {
        isEmailValid && isPasswordValid && nickname.isNotBlank()
    }

    fun submit() {
        if (!canProceed) return
        isSubmitting = true
        scope.launch {
            try {
                val auth = if (isLoginMode) {
                    val res = RetrofitClient.api.login(LoginRequest(email.trim(), password))
                    if (!res.success || res.data == null) throw ApiException(res.error)
                    res.data
                } else {
                    val res = RetrofitClient.api.register(
                        RegisterRequest(email = email.trim(), password = password, nickname = nickname.trim())
                    )
                    if (!res.success || res.data == null) throw ApiException(res.error)
                    res.data
                }
                TokenManager.saveTokens(auth.accessToken, auth.refreshToken)
                isSubmitting = false
                onLoginSuccess()
            } catch (e: Exception) {
                isSubmitting = false
                val message = (e as? ApiException)?.errorBody?.message
                    ?: if (isLoginMode) "로그인에 실패했어요. 이메일/비밀번호를 확인해주세요."
                       else "회원가입에 실패했어요. 잠시 후 다시 시도해주세요."
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    // 공통 입력 필드 색상
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor     = TextPrimary,
        unfocusedTextColor   = TextPrimary,
        focusedContainerColor   = BgCard,
        unfocusedContainerColor = BgCard,
        // ✅ 포커스 시 AccentYellow 테두리 — 다른 화면과 통일
        focusedBorderColor   = AccentYellow,
        unfocusedBorderColor = BorderSubtle
    )

    Box(Modifier.fillMaxSize().background(BgPrimary)) {

        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Hero Image — 고정 높이로 바꿔서 키보드 올라와도 안 밀림
            Box(Modifier.fillMaxWidth().height(260.dp)) {
                AsyncImage(
                    model              = "https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=420&h=380&fit=crop&auto=format&q=90",
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0x4D0C0B0A), Color.Transparent, BgPrimary))
                    )
                )
                Text(
                    text       = "WannaWear",
                    color      = TextPrimary,
                    fontSize   = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle  = FontStyle.Italic,
                    modifier   = Modifier.padding(24.dp).align(Alignment.TopStart)
                )
            }

            // 하단 패널 — 이제 일반 플로우 안에 있어서 스크롤 대상에 포함됨
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            Text(
                "입지 않는 옷에\n새 주인을 찾아줘요",
                color      = TextPrimary,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 30.sp
            )
            Text("스와이프로 교환 · 지구에 한 뼘 더 가깝게",
                color = TextSecondary, fontSize = 11.sp)

            Spacer(Modifier.height(4.dp))

            // 로그인 / 회원가입 탭
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .padding(4.dp)
            ) {
                listOf(true to "로그인", false to "회원가입").forEach { (isLogin, label) ->
                    val selected = isLoginMode == isLogin
                    Button(
                        onClick  = { isLoginMode = isLogin },
                        modifier = Modifier.weight(1f).height(36.dp),
                        shape    = RoundedCornerShape(8.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = if (selected) AccentYellow else Color.Transparent,
                            contentColor   = if (selected) AccentYellowText else TextSecondary
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // 입력 필드
            OutlinedTextField(
                value         = email,
                onValueChange = { email = it },
                placeholder   = { Text("이메일", color = TextSecondary) },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors        = fieldColors,
                // ✅ 이메일 형식 오류 시 빨간 테두리 힌트
                isError       = email.isNotEmpty() && !isEmailValid
            )
            OutlinedTextField(
                value                  = password,
                onValueChange          = { password = it },
                placeholder            = { Text("비밀번호 (6자 이상)", color = TextSecondary) },
                visualTransformation   = PasswordVisualTransformation(),
                modifier               = Modifier.fillMaxWidth(),
                shape                  = RoundedCornerShape(12.dp),
                singleLine             = true,
                keyboardOptions        = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors                 = fieldColors,
                isError                = password.isNotEmpty() && !isPasswordValid
            )
            if (!isLoginMode) {
                OutlinedTextField(
                    value         = nickname,
                    onValueChange = { nickname = it },
                    placeholder   = { Text("닉네임", color = TextSecondary) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    singleLine    = true,
                    colors        = fieldColors
                )
            }

            // ✅ canProceed 가 false면 버튼 비활성화, 제출 중엔 로딩 표시
            Button(
                onClick  = { submit() },
                enabled  = canProceed,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor        = AccentYellow,
                    contentColor          = AccentYellowText,
                    disabledContainerColor = BgCardDark,
                    disabledContentColor   = TextTertiary
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = AccentYellowText,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        if (isLoginMode) "로그인" else "가입하고 시작하기",
                        fontWeight = FontWeight.Black,
                        fontSize   = 14.sp
                    )
                }
            }

            // 구분선
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Divider(Modifier.weight(1f), color = BorderSubtle)
                Text("  또는  ", color = TextTertiary, fontSize = 10.sp)
                Divider(Modifier.weight(1f), color = BorderSubtle)
            }

            // 소셜 버튼 — ⚠️ 카카오/Apple OAuth는 백엔드 미구현이라 임시로 안내만 띄움
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick  = { scope.launch { snackbarHostState.showSnackbar("카카오 로그인은 아직 준비 중이에요") } },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = KakaoBg, contentColor = KakaoText)
                ) {
                    Text("💬  카카오", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Button(
                    onClick  = { scope.launch { snackbarHostState.showSnackbar("Apple 로그인은 아직 준비 중이에요") } },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = BgCard, contentColor = TextPrimary)
                ) {
                    Text("  Apple", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        } // close 스크롤 가능한 outer Column

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
