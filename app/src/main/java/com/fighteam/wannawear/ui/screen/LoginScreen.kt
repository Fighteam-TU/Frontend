package com.fighteam.wannawear.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fighteam.wannawear.ui.theme.*

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(BgPrimary)) {

        // Hero Image (상단 42%)
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.42f)) {
            AsyncImage(
                model = "https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=420&h=380&fit=crop&auto=format&q=90",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 그라디언트 오버레이
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0x4D0C0B0A), Color.Transparent, BgPrimary)
                    )
                )
            )
            // 로고
            Text(
                text = "WannaWear",
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(24.dp).align(Alignment.TopStart)
            )
        }

        // 하단 패널
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 헤드라인
            Text("입지 않는 옷에\n새 주인을 찾아줘요", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)
            Text("스와이프로 교환 · 지구에 한 뼘 더 가깝게", color = TextSecondary, fontSize = 11.sp)

            Spacer(Modifier.height(4.dp))

            // 탭
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BgCard).padding(4.dp)
            ) {
                listOf(true to "로그인", false to "회원가입").forEach { (isLogin, label) ->
                    val selected = isLoginMode == isLogin
                    Button(
                        onClick = { isLoginMode = isLogin },
                        modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) AccentYellow else Color.Transparent,
                            contentColor = if (selected) AccentYellowText else TextSecondary
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
            }

            // 입력 필드
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                placeholder = { Text("이메일", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                    focusedBorderColor = BorderSubtle, unfocusedBorderColor = BorderSubtle
                )
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                placeholder = { Text("비밀번호", color = TextSecondary) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                    focusedBorderColor = BorderSubtle, unfocusedBorderColor = BorderSubtle
                )
            )
            if (!isLoginMode) {
                OutlinedTextField(
                    value = nickname, onValueChange = { nickname = it },
                    placeholder = { Text("닉네임", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                        focusedBorderColor = BorderSubtle, unfocusedBorderColor = BorderSubtle
                    )
                )
            }

            // CTA 버튼
            Button(
                onClick = onLoginSuccess,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentYellow, contentColor = AccentYellowText)
            ) {
                Text(if (isLoginMode) "로그인" else "가입하고 시작하기", fontWeight = FontWeight.Black, fontSize = 14.sp)
            }

            // 구분선
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Divider(Modifier.weight(1f), color = BorderSubtle)
                Text("  또는  ", color = TextTertiary, fontSize = 10.sp)
                Divider(Modifier.weight(1f), color = BorderSubtle)
            }

            // 소셜 버튼
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onLoginSuccess,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KakaoBg, contentColor = KakaoText)
                ) { Text("💬  카카오", fontWeight = FontWeight.Bold, fontSize = 12.sp) }

                Button(
                    onClick = onLoginSuccess,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BgCard, contentColor = TextPrimary)
                ) { Text("  Apple", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }
    }
}
