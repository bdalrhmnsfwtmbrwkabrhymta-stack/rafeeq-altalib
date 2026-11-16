package com.hawas.study.dofaati.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hawas.study.dofaati.DofaatiViewModel
import com.hawas.study.dofaati.models.DofaatiChatMessage
import com.hawas.study.dofaati.ui.theme.DofaatiAdaptiveScreenContainer
import com.hawas.study.dofaati.ui.theme.DofaatiAudioWaveform
import com.hawas.study.dofaati.ui.theme.DofaatiColors
import com.hawas.study.dofaati.ui.theme.DofaatiLtrText
import com.hawas.study.dofaati.ui.theme.DofaatiRoyalBlueHeader
import com.hawas.study.dofaati.ui.theme.formatDurationLtr
import com.example.ui.theme.ReadexProFontFamily

@Composable
fun BatchChatAndAdminContainerScreen(viewModel: DofaatiViewModel) {
    var selectedSubPane by remember { mutableIntStateOf(0) } // 0 = Chat, 1 = Admin Requests

    DofaatiAdaptiveScreenContainer(
        useSafeDrawing = false,
        landscapeLeftWeight = 0.55f,
        portraitContent = {
            Column(modifier = Modifier.fillMaxSize()) {
                if (selectedSubPane == 0) {
                    BatchChatScreen(
                        viewModel = viewModel,
                        onSwitchToAdmin = { selectedSubPane = 1 }
                    )
                } else {
                    JoinRequestsAdminScreen(
                        viewModel = viewModel,
                        onSwitchToChat = { selectedSubPane = 0 }
                    )
                }
            }
        },
        landscapeLeftPane = {
            BatchChatScreen(
                viewModel = viewModel,
                onSwitchToAdmin = { selectedSubPane = 1 }
            )
        },
        landscapeRightPane = {
            JoinRequestsAdminScreen(
                viewModel = viewModel,
                onSwitchToChat = { selectedSubPane = 0 }
            )
        }
    )
}

@Composable
fun BatchChatScreen(
    viewModel: DofaatiViewModel,
    onSwitchToAdmin: () -> Unit = {}
) {
    val context = LocalContext.current
    val batches by viewModel.batches.collectAsStateWithLifecycle()
    val activeBatchId by viewModel.activeBatchId.collectAsStateWithLifecycle()
    val batch = remember(batches, activeBatchId) {
        batches.firstOrNull { it.id == activeBatchId } ?: batches.first()
    }
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val replyingTo by viewModel.replyingToMessage.collectAsStateWithLifecycle()
    val playingAudioId by viewModel.playingAudioId.collectAsStateWithLifecycle()
    val progressFraction by viewModel.audioProgressFraction.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecordingVoice.collectAsStateWithLifecycle()
    val joinRequests by viewModel.joinRequests.collectAsStateWithLifecycle()

    var inputMessage by remember { mutableStateOf("") }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.toggleVoiceRecording(hasMicPermission = granted)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DofaatiColors.Background)
            .navigationBarsPadding()
            .testTag("batch_chat_screen")
    ) {
        DofaatiRoyalBlueHeader(
            title = "شات الدفعة - ${batch.name}",
            onNavigateBack = { viewModel.navigateBack() },
            trailingContent = {
                TextButton(
                    onClick = onSwitchToAdmin,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    modifier = Modifier.testTag("btn_switch_to_admin_panel")
                ) {
                    Icon(Icons.Default.ManageAccounts, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "الطلبات (${joinRequests.size})",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            },
            testTag = "batch_chat_header"
        )

        // Orange Pinned Announcement Capsule (#F97316) matching Mockup 3
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("chat_pinned_orange_banner"),
            shape = RoundedCornerShape(24.dp),
            color = DofaatiColors.PinnedOrange,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Campaign, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = batch.pinnedNotice,
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatMessageBubble(
                    message = msg,
                    isPlaying = playingAudioId == msg.id,
                    progressFraction = if (playingAudioId == msg.id) progressFraction else 0.35f,
                    onPlayToggle = { viewModel.toggleAudioPlayback(msg.id) },
                    onReply = { viewModel.setReplyMessage(msg) },
                    onReact = { emoji -> viewModel.toggleMessageReaction(msg.id, emoji) }
                )
            }
        }

        if (replyingTo != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = DofaatiColors.RoyalBlueSoft
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "رد على ${replyingTo!!.senderName}: ${replyingTo!!.text}",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 12.sp,
                        color = DofaatiColors.RoyalBlueDark,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.setReplyMessage(null) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "إلغاء الرد", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Composer Row with Text Field, Send & Voice Recording
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputMessage,
                    onValueChange = { inputMessage = it },
                    placeholder = {
                        Text(
                            text = if (isRecording) "جاري تسجيل رسالة صوتية..." else "اكتب رسالة للدفعة...",
                            fontFamily = ReadexProFontFamily,
                            fontSize = 13.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )

                FilledIconButton(
                    onClick = {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            viewModel.toggleVoiceRecording(hasMicPermission = true)
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isRecording) DofaatiColors.RedAlert else DofaatiColors.RoyalBlueSoft,
                        contentColor = if (isRecording) Color.White else DofaatiColors.RoyalBlue
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chat_mic_button")
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording) "إيقاف وإرسال التسجيل" else "تسجيل صوتي"
                    )
                }

                FilledIconButton(
                    onClick = {
                        viewModel.sendTextChatMessage(inputMessage)
                        inputMessage = ""
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = DofaatiColors.RoyalBlue),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chat_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: DofaatiChatMessage,
    isPlaying: Boolean,
    progressFraction: Float,
    onPlayToggle: () -> Unit,
    onReply: () -> Unit,
    onReact: (String) -> Unit
) {
    val bubbleBg = if (message.isMe) DofaatiColors.RoyalBlueSoft else Color.White
    val borderCol = if (message.isMe) DofaatiColors.RoyalBlueTint else DofaatiColors.Border

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = bubbleBg,
        border = BorderStroke(1.dp, borderCol)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (message.isAdmin) DofaatiColors.RoyalBlue else DofaatiColors.NavyInk),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(message.senderInitial, fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message.senderName,
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = DofaatiColors.NavyInk
                    )
                    if (message.isAdmin) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = DofaatiColors.RoyalBlueSoft) {
                            Text(
                                text = "مشرف",
                                fontFamily = ReadexProFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = DofaatiColors.RoyalBlue,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(message.timestamp, fontFamily = ReadexProFontFamily, fontSize = 11.sp, color = DofaatiColors.SlateMuted)
            }

            if (!message.replyToPreview.isNullOrBlank()) {
                Surface(shape = RoundedCornerShape(8.dp), color = DofaatiColors.Background) {
                    Text(
                        text = "↩ ${message.replyToPreview}",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 11.5.sp,
                        color = DofaatiColors.SlateMuted,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (message.isAudio) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledIconButton(
                        onClick = onPlayToggle,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = DofaatiColors.RoyalBlue),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "تشغيل الرسالة الصوتية",
                            tint = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        DofaatiAudioWaveform(
                            peaks = message.waveformPeaks,
                            progressFraction = progressFraction
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(message.text, fontFamily = ReadexProFontFamily, fontSize = 11.5.sp, color = DofaatiColors.SlateBody)
                            DofaatiLtrText(
                                text = formatDurationLtr(message.audioDurationSec),
                                fontSize = 11.5.sp,
                                color = DofaatiColors.SlateMuted
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = message.text,
                    fontFamily = ReadexProFontFamily,
                    fontSize = 14.sp,
                    color = DofaatiColors.NavyInk,
                    lineHeight = 20.sp
                )
            }

            // Emoji Reactions & Reply Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("👍", "❤️", "🔥").forEach { emoji ->
                        val count = message.reactions[emoji] ?: 0
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onReact(emoji) },
                            shape = RoundedCornerShape(10.dp),
                            color = DofaatiColors.Background
                        ) {
                            Text(
                                text = if (count > 0) "$emoji $count" else emoji,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                TextButton(onClick = onReply, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("رد", fontFamily = ReadexProFontFamily, fontSize = 12.sp)
                }
            }
        }
    }
}
