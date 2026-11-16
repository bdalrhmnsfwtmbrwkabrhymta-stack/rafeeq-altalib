package com.hawas.study.dofaati.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hawas.study.dofaati.DofaatiViewModel
import com.hawas.study.dofaati.ui.theme.DofaatiCard
import com.hawas.study.dofaati.ui.theme.DofaatiColors
import com.hawas.study.dofaati.ui.theme.DofaatiLtrText
import com.hawas.study.dofaati.ui.theme.DofaatiRoyalBlueHeader
import com.example.ui.theme.ReadexProFontFamily

@Composable
fun JoinRequestsAdminScreen(
    viewModel: DofaatiViewModel,
    onSwitchToChat: () -> Unit = {}
) {
    val context = LocalContext.current
    val batches by viewModel.batches.collectAsStateWithLifecycle()
    val activeBatchId by viewModel.activeBatchId.collectAsStateWithLifecycle()
    val batch = remember(batches, activeBatchId) {
        batches.firstOrNull { it.id == activeBatchId } ?: batches.first()
    }
    val joinRequests by viewModel.joinRequests.collectAsStateWithLifecycle()
    val members by viewModel.batchMembers.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DofaatiColors.Background)
            .navigationBarsPadding()
            .testTag("join_requests_admin_screen")
    ) {
        DofaatiRoyalBlueHeader(
            title = "إدارة الدفعة - طلبات الانضمام",
            onNavigateBack = { viewModel.navigateBack() },
            trailingContent = {
                TextButton(
                    onClick = onSwitchToChat,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    modifier = Modifier.testTag("btn_switch_back_to_chat")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("الشات", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            testTag = "admin_requests_header"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Prominent White Card showing 8-digit Batch Code in bold LTR Royal Blue (4861 3927)
            item {
                DofaatiCard(modifier = Modifier.testTag("admin_batch_code_card")) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "كود الدفعة",
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = DofaatiColors.SlateMuted
                        )
                        DofaatiLtrText(
                            text = batch.code,
                            fontSize = 30.sp,
                            letterSpacing = 2.sp,
                            color = DofaatiColors.RoyalBlue,
                            modifier = Modifier.testTag("batch_code_ltr_value")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("dofaati_code", batch.code))
                                    Toast.makeText(context, "تم نسخ الكود ${batch.code}", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("نسخ الكود", fontFamily = ReadexProFontFamily, fontSize = 12.5.sp)
                            }
                            Button(
                                onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "كود الانضمام لدفعة ${batch.name}: ${batch.code}")
                                    }
                                    runCatching { context.startActivity(Intent.createChooser(send, "مشاركة الكود")) }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("مشاركة", fontFamily = ReadexProFontFamily, fontSize = 12.5.sp)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "طلبات الانضمام المعلقة (${joinRequests.size})",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = DofaatiColors.NavyInk
                )
            }

            if (joinRequests.isEmpty()) {
                item {
                    DofaatiCard {
                        Text(
                            text = "لا توجد طلبات انضمام معلقة حالياً.",
                            fontFamily = ReadexProFontFamily,
                            fontSize = 13.sp,
                            color = DofaatiColors.SlateMuted,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(joinRequests, key = { it.id }) { req ->
                    DofaatiCard(modifier = Modifier.testTag("join_request_${req.id}")) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(DofaatiColors.RoyalBlueSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(req.studentInitial, fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold, color = DofaatiColors.RoyalBlue)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(req.studentName, fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = DofaatiColors.NavyInk)
                                Text("${req.note} • ${req.requestedAt}", fontFamily = ReadexProFontFamily, fontSize = 11.5.sp, color = DofaatiColors.SlateMuted)
                            }
                            IconButton(
                                onClick = { viewModel.approveJoinRequest(req.id) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("btn_approve_${req.id}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(DofaatiColors.EmeraldGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "قبول", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(
                                onClick = { viewModel.rejectJoinRequest(req.id) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("btn_reject_${req.id}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(DofaatiColors.RedAlert),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "رفض", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "أعضاء الدفعة (${members.size})",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = DofaatiColors.NavyInk
                )
            }

            items(members, key = { it.id }) { member ->
                DofaatiCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(member.name, fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DofaatiColors.NavyInk)
                            Text(
                                text = if (member.isMuted) "${member.roleLabel} • مكتوم مؤقتاً" else member.roleLabel,
                                fontFamily = ReadexProFontFamily,
                                fontSize = 12.sp,
                                color = if (member.isMuted) DofaatiColors.RedAlert else DofaatiColors.SlateMuted
                            )
                        }
                        if (!member.isAdmin) {
                            IconButton(onClick = { viewModel.toggleMuteMember(member.id) }, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    imageVector = if (member.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "كتم العضو",
                                    tint = DofaatiColors.AmberGold
                                )
                            }
                            IconButton(onClick = { viewModel.removeMember(member.id) }, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "حذف العضو",
                                    tint = DofaatiColors.RedAlert
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
