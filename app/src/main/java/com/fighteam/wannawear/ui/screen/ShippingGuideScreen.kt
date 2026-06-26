package com.fighteam.wannawear.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fighteam.wannawear.data.AppState
import com.fighteam.wannawear.ui.theme.*

@Composable
fun ShippingGuideScreen(matchId: Int, onBack: () -> Unit) {
    val match = AppState.matches.firstOrNull { it.id == matchId }
        ?: run { onBack(); return }

    val clipboard = LocalClipboardManager.current
    var copiedAddress  by remember { mutableStateOf(false) }
    var shippingStarted by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(BgPrimary)) {

        // 앱바
        Row(
            Modifier.fillMaxWidth().background(BgCard)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextPrimary)
            }
            Text("배송 안내", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f).padding(start = 4.dp))
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // 교환 아이템 요약
            Row(
                Modifier.fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(match.myItem.image, null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                Column(
                    Modifier.weight(1f).padding(horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("⇄", color = AccentYellow, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("교환", color = TextSecondary, fontSize = 9.sp)
                }
                AsyncImage(match.theirItem.image, null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            }

            Spacer(Modifier.height(20.dp))

            // 배송 단계 안내
            SectionTitle("📦 배송 방법")
            Spacer(Modifier.height(12.dp))

            ShippingStep(
                step  = 1,
                icon  = Icons.Default.ShoppingBag,
                title = "옷을 깨끗하게 포장해요",
                desc  = "비닐백이나 박스에 옷을 넣고 찢어지지 않게 테이프로 고정해주세요.",
                illustration = { PackagingIllustration() }
            )
            StepConnector()
            ShippingStep(
                step  = 2,
                icon  = Icons.Default.LocalShipping,
                title = "택배사를 선택해요",
                desc  = "CJ대한통운 / 한진 / 롯데택배 중 편한 곳을 이용하세요.\n편의점 택배도 가능해요 (CU, GS25, 세븐일레븐).",
                illustration = { CarrierIllustration() }
            )
            StepConnector()
            ShippingStep(
                step  = 3,
                icon  = Icons.Default.Place,
                title = "상대방 주소로 발송해요",
                desc  = "아래 주소를 수취인으로 입력하세요.",
                illustration = null
            )

            // 수령 주소 박스
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth()
                    .border(1.dp, AccentYellow.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .background(AccentYellow.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("수취인 주소", color = AccentYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(match.partnerAddress, color = TextPrimary, fontSize = 14.sp,
                        fontWeight = FontWeight.Bold, lineHeight = 20.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("수취인: ${match.partner.name}", color = TextSecondary, fontSize = 11.sp)
                }
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(match.partnerAddress))
                    copiedAddress = true
                }) {
                    Icon(
                        if (copiedAddress) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "주소 복사",
                        tint = if (copiedAddress) StatusComplete else AccentYellow,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            StepConnector()
            ShippingStep(
                step  = 4,
                icon  = Icons.Default.ChatBubbleOutline,
                title = "운송장 번호를 공유해요",
                desc  = "채팅창에서 상대방에게 운송장 번호를 알려주세요. 상대방도 동일하게 진행해요.",
                illustration = { TrackingIllustration() }
            )

            Spacer(Modifier.height(24.dp))

            // 주의사항
            SectionTitle("⚠️ 주의사항")
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "옷 상태가 설명과 다를 경우 교환이 취소될 수 있어요",
                    "배송비는 각자 부담이에요",
                    "분실·파손 시 해당 택배사에 보상 신청하세요",
                    "발송 후 운송장 번호를 채팅으로 꼭 공유해주세요"
                ).forEach { note ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", color = TextSecondary, fontSize = 12.sp)
                        Text(note, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // 배송 완료 버튼
            if (!shippingStarted) {
                Button(
                    onClick = {
                        AppState.startShipping(matchId)
                        shippingStarted = true
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = AccentYellow, contentColor = AccentYellowText)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("배송 완료 (발송했어요)", fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth()
                        .background(StatusComplete.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                        tint = StatusComplete, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("발송 완료됐어요! 상대방 수령을 기다려주세요",
                        color = StatusComplete, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("돌아가기", color = TextTertiary)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ── 공통 컴포넌트 ──────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black)
}

@Composable
private fun StepConnector() {
    Box(Modifier.padding(start = 20.dp)) {
        Box(Modifier.width(2.dp).height(20.dp).background(BorderSubtle))
    }
}

@Composable
private fun ShippingStep(
    step: Int,
    icon: ImageVector,
    title: String,
    desc: String,
    illustration: (@Composable () -> Unit)?
) {
    Row(
        Modifier.fillMaxWidth()
            .background(BgCard, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.size(36.dp).background(AccentYellow, CircleShape), contentAlignment = Alignment.Center) {
            Text("$step", color = AccentYellowText, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = AccentYellow, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(desc, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
            if (illustration != null) {
                Spacer(Modifier.height(12.dp))
                illustration()
            }
        }
    }
}

// ── 일러스트 ──────────────────────────────────────────────────────────

@Composable
private fun PackagingIllustration() {
    Row(
        Modifier.fillMaxWidth()
            .background(BgCardDark, RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("👕", fontSize = 28.sp)
            Text("옷", color = TextSecondary, fontSize = 10.sp)
        }
        Text("→", color = TextTertiary, fontSize = 18.sp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(40.dp, 48.dp)
                    .background(Color(0x33E4D94A), RoundedCornerShape(4.dp))
                    .border(1.dp, AccentYellow.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("비닐", color = AccentYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text("포장", color = TextSecondary, fontSize = 10.sp)
        }
        Text("→", color = TextTertiary, fontSize = 18.sp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📦", fontSize = 28.sp)
            Text("박스", color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun CarrierIllustration() {
    Row(
        Modifier.fillMaxWidth()
            .background(BgCardDark, RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf(
            Triple("🚚", "CJ대한통운", "전국"),
            Triple("🚐", "한진택배",   "전국"),
            Triple("🏪", "편의점",     "CU·GS")
        ).forEach { (emoji, name, sub) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(emoji, fontSize = 24.sp)
                Text(name, color = TextPrimary,   fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(sub,  color = TextSecondary, fontSize = 9.sp,  textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun TrackingIllustration() {
    Row(
        Modifier.fillMaxWidth()
            .background(BgCardDark, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            Modifier.weight(1f)
                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Text("운송장",         color = TextTertiary, fontSize = 9.sp)
            Text("1234-5678-9012", color = TextPrimary,  fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Icon(Icons.Default.ArrowForward, contentDescription = null,
            tint = AccentYellow, modifier = Modifier.size(16.dp))
        Column(
            Modifier.weight(1f)
                .background(AccentYellow.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Text("채팅으로 공유",  color = AccentYellow,  fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("상대방에게\n운송장 번호를 알려요", color = TextSecondary, fontSize = 9.sp, lineHeight = 13.sp)
        }
    }
}
