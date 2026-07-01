package com.fighteam.wannawear.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fighteam.wannawear.data.AppState
import com.fighteam.wannawear.data.model.Address
import com.fighteam.wannawear.ui.theme.*

@Composable
fun AddressScreen(onBack: () -> Unit) {
    val addresses = AppState.addresses
    var showAddForm by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Address?>(null) }

    LaunchedEffect(Unit) { AppState.loadAddresses() }

    deleteTarget?.let { addr ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("이 주소를 삭제할까요?", fontWeight = FontWeight.Bold) },
            text  = { Text(addr.address1) },
            confirmButton = {
                TextButton(onClick = {
                    AppState.deleteAddress(addr.id)
                    deleteTarget = null
                }) { Text("삭제", color = PassColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("취소") }
            }
        )
    }

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
            Text("배송지 관리", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f).padding(start = 4.dp))
        }

        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                "교환을 확정(confirm)하려면 기본 배송지가 1개 있어야 해요. 상대방이 확정하면 이 주소가 상대방에게 보여요.",
                color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))

            if (AppState.isLoading && addresses.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentYellow)
                }
            } else if (addresses.isEmpty() && !showAddForm) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📮", fontSize = 32.sp)
                        Text("등록된 배송지가 없어요", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("아래에서 배송지를 추가해주세요", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            } else {
                addresses.forEach { addr ->
                    AddressCard(address = addr, onDelete = { deleteTarget = addr })
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            if (showAddForm) {
                AddressForm(
                    onCancel = { showAddForm = false },
                    onSubmit = { address1, recipient, postalCode, label, isDefault ->
                        AppState.addAddress(
                            address1 = address1,
                            recipient = recipient.ifBlank { null },
                            postalCode = postalCode.ifBlank { null },
                            label = label.ifBlank { null },
                            isDefault = isDefault
                        ) { success ->
                            if (success) showAddForm = false
                        }
                    }
                )
            } else {
                OutlinedButton(
                    onClick = { showAddForm = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentYellow)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("새 배송지 추가", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AddressCard(address: Address, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(BgCard, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(address.label.ifBlank { "배송지" }, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (address.isDefault) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "기본", color = AccentYellowText, fontSize = 9.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.background(AccentYellow, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(address.address1, color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
            if (address.address2.isNotBlank()) {
                Text(address.address2, color = TextSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            val meta = listOfNotNull(
                address.recipient.ifBlank { null },
                address.postalCode.ifBlank { null }
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, color = TextTertiary, fontSize = 11.sp)
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "삭제", tint = TextTertiary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AddressForm(
    onCancel: () -> Unit,
    onSubmit: (address1: String, recipient: String, postalCode: String, label: String, isDefault: Boolean) -> Unit
) {
    var address1 by remember { mutableStateOf("") }
    var address2 by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf("") }
    var postalCode by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(true) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
        focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
        focusedBorderColor = AccentYellow, unfocusedBorderColor = BorderSubtle
    )

    Column(
        Modifier.fillMaxWidth().background(BgCardDark, RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("새 배송지", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = address1, onValueChange = { address1 = it },
            placeholder = { Text("주소 *  ex) 서울특별시 중구 세종대로 110", color = TextSecondary, fontSize = 12.sp) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp), colors = fieldColors
        )
        OutlinedTextField(
            value = address2, onValueChange = { address2 = it },
            placeholder = { Text("상세 주소 (선택)  ex) 1층", color = TextSecondary, fontSize = 12.sp) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp), colors = fieldColors
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = recipient, onValueChange = { recipient = it },
                placeholder = { Text("받는 사람", color = TextSecondary, fontSize = 12.sp) },
                singleLine = true, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp), colors = fieldColors
            )
            OutlinedTextField(
                value = postalCode, onValueChange = { postalCode = it },
                placeholder = { Text("우편번호", color = TextSecondary, fontSize = 12.sp) },
                singleLine = true, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp), colors = fieldColors
            )
        }
        OutlinedTextField(
            value = label, onValueChange = { label = it },
            placeholder = { Text("라벨 (선택)  ex) 집, 회사", color = TextSecondary, fontSize = 12.sp) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp), colors = fieldColors
        )

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(BgCard).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("기본 배송지로 설정", color = TextSecondary, fontSize = 12.sp)
            Switch(
                checked = isDefault,
                onCheckedChange = { isDefault = it },
                colors = SwitchDefaults.colors(checkedThumbColor = AccentYellowText, checkedTrackColor = AccentYellow)
            )
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) { Text("취소") }
            Button(
                onClick = { if (address1.isNotBlank()) onSubmit(address1, recipient, postalCode, label, isDefault) },
                enabled = address1.isNotBlank(),
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentYellow, contentColor = AccentYellowText,
                    disabledContainerColor = BgCard, disabledContentColor = TextTertiary
                )
            ) { Text("저장", fontWeight = FontWeight.Bold) }
        }
    }
}
