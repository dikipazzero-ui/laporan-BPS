package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChatMessage
import com.example.data.ChatMessageType
import com.example.data.TeamMember
import com.example.data.UiState
import com.example.ui.components.MemberAvatar
import com.example.util.DateTimeUtils
import com.example.util.MediaUtils
import com.example.util.VoicePlaybackState
import com.example.util.VoicePlayerController
import com.example.util.VoiceRecorderController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun LiveChatScreen(
    messagesState: UiState<List<ChatMessage>>,
    teamMembers: List<TeamMember>,
    currentUserId: String,
    myDisplayName: String,
    myPhotoBase64: String?,
    isAdmin: Boolean,
    onEditProfileClick: () -> Unit,
    onSendText: (String) -> Unit,
    onSendImage: (String) -> Unit,
    onSendAudio: (String, Int) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onPreviewPhoto: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val recorder = remember(context) { VoiceRecorderController(context) }
    val player = remember(context, scope) { VoicePlayerController(context, scope) }
    val playbackState by player.state.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            recorder.stopAndDiscard()
            player.stop()
        }
    }

    var messageText by remember { mutableStateOf("") }
    var showAttachMenu by remember { mutableStateOf(false) }
    var isProcessingPhoto by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val memberMap = remember(teamMembers) {
        teamMembers.associateBy { it.userId }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording && recordingSeconds < 60) {
                delay(1000L)
                if (isRecording) recordingSeconds += 1
            }
            if (isRecording && recordingSeconds >= 60) {
                isRecording = false
                val res = recorder.stopAndEncode()
                if (res != null) {
                    onSendAudio(res.first, res.second)
                }
            }
        }
    }

    val messagesList = (messagesState as? UiState.Success)?.data ?: emptyList()
    LaunchedEffect(messagesList.size) {
        if (messagesList.isNotEmpty()) {
            listState.animateScrollToItem(messagesList.size - 1)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            scope.launch {
                isProcessingPhoto = true
                val encoded = MediaUtils.compressImageUriToBase64(
                    context = context,
                    uri = uri,
                    maxDimension = 960,
                    maxBytes = 180_000
                )
                isProcessingPhoto = false
                if (encoded != null) onSendImage(encoded)
                else onShowMessage("Gagal memproses foto dari kamera.")
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = MediaUtils.createTempCameraUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            onShowMessage("Izin kamera dibutuhkan untuk mengirim foto dari kamera.")
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isProcessingPhoto = true
                val encoded = MediaUtils.compressImageUriToBase64(
                    context = context,
                    uri = uri,
                    maxDimension = 960,
                    maxBytes = 180_000
                )
                isProcessingPhoto = false
                if (encoded != null) onSendImage(encoded)
                else onShowMessage("Gagal memproses foto dari galeri.")
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (recorder.startRecording()) {
                isRecording = true
            } else {
                onShowMessage("Mikrofon tidak tersedia untuk merekam suara.")
            }
        } else {
            onShowMessage("Izin mikrofon dibutuhkan untuk merekam pesan suara.")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Quick Profile Banner inside Chat
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    MemberAvatar(
                        displayName = myDisplayName,
                        photoBase64 = myPhotoBase64,
                        size = 38.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = myDisplayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${teamMembers.size} anggota tim online",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .clickable(onClick = onEditProfileClick)
                        .testTag("chat_edit_profile_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Ubah Profil",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Messages List
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (messagesState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = messagesState.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                is UiState.Success -> {
                    if (messagesState.data.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Live Chat Tim BPS Siap Digunakan",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Kirim pesan teks, foto dokumentasi, atau rekam pesan suara untuk koordinasi cepat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(messagesState.data, key = { it.id }) { msg ->
                                val liveMember = memberMap[msg.senderId]
                                val displaySenderName = liveMember?.displayName?.ifBlank { msg.senderName } ?: msg.senderName
                                val displaySenderPhoto = liveMember?.photoBase64 ?: msg.senderPhotoBase64
                                val isMine = msg.senderId == currentUserId

                                ChatBubbleRow(
                                    message = msg,
                                    displaySenderName = displaySenderName,
                                    displaySenderPhoto = displaySenderPhoto,
                                    isMine = isMine,
                                    canDelete = isMine || isAdmin,
                                    playbackState = playbackState,
                                    onToggleVoicePlay = { player.togglePlay(msg.id, msg.audioBase64) },
                                    onPreviewPhoto = onPreviewPhoto,
                                    onDelete = { onDeleteMessage(msg.id) }
                                )
                            }
                        }
                    }
                }
            }

            if (isProcessingPhoto) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 4.dp,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Mengirim foto...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Composer Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isRecording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = String.format(Locale.US, "Merekam 00:%02d / 01:00", recordingSeconds),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                isRecording = false
                                recorder.stopAndDiscard()
                            },
                            modifier = Modifier.testTag("cancel_voice_button")
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Batalkan rekaman")
                        }

                        FilledIconButton(
                            onClick = {
                                isRecording = false
                                val encoded = recorder.stopAndEncode()
                                if (encoded != null) {
                                    onSendAudio(encoded.first, encoded.second)
                                } else {
                                    onShowMessage("Rekaman suara terlalu singkat.")
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("send_voice_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim pesan suara")
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconButton(
                            onClick = { showAttachMenu = true },
                            modifier = Modifier.testTag("chat_attach_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddPhotoAlternate,
                                contentDescription = "Kirim foto",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = showAttachMenu,
                            onDismissRequest = { showAttachMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ambil dari Kamera") },
                                leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                                onClick = {
                                    showAttachMenu = false
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) {
                                        val uri = MediaUtils.createTempCameraUri(context)
                                        pendingCameraUri = uri
                                        cameraLauncher.launch(uri)
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Pilih dari Galeri") },
                                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                                onClick = {
                                    showAttachMenu = false
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { if (it.length <= 1500) messageText = it },
                        placeholder = { Text("Tulis pesan tim...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input")
                    )

                    Spacer(Modifier.width(6.dp))

                    if (messageText.trim().isNotEmpty()) {
                        FilledIconButton(
                            onClick = {
                                val toSend = messageText.trim()
                                messageText = ""
                                onSendText(toSend)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_send_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim pesan")
                        }
                    } else {
                        FilledIconButton(
                            onClick = {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    if (recorder.startRecording()) {
                                        isRecording = true
                                    } else {
                                        onShowMessage("Tidak dapat mengakses mikrofon.")
                                    }
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_record_voice_button")
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = "Rekam voice note")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleRow(
    message: ChatMessage,
    displaySenderName: String,
    displaySenderPhoto: String?,
    isMine: Boolean,
    canDelete: Boolean,
    playbackState: VoicePlaybackState,
    onToggleVoicePlay: () -> Unit,
    onPreviewPhoto: (String) -> Unit,
    onDelete: () -> Unit
) {
    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surface

    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface

    val mutedColor = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMine) {
            MemberAvatar(
                displayName = displaySenderName,
                photoBase64 = displaySenderPhoto,
                size = 34.dp
            )
            Spacer(Modifier.width(8.dp))
        }

        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 18.dp
            ),
            tonalElevation = if (isMine) 0.dp else 1.dp,
            modifier = Modifier.widthIn(min = 120.dp, max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isMine) "Anda ($displaySenderName)" else displaySenderName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isMine) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.primary
                    )
                    if (canDelete) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Hapus pesan",
                            tint = mutedColor,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable(onClick = onDelete)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                when (message.messageType) {
                    ChatMessageType.TEXT -> {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                    ChatMessageType.IMAGE -> {
                        val bmp = remember(message.imageBase64) {
                            MediaUtils.decodeBase64ToImageBitmap(message.imageBase64)
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = "Foto chat",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { message.imageBase64?.let(onPreviewPhoto) },
                                contentScale = ContentScale.Crop
                            )
                        }
                        if (message.text.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor
                            )
                        }
                    }
                    ChatMessageType.AUDIO -> {
                        val isThisPlaying = playbackState.activeMessageId == message.id && playbackState.isPlaying
                        val isThisActive = playbackState.activeMessageId == message.id
                        val progress = if (isThisActive && playbackState.durationMs > 0) {
                            (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = onToggleVoicePlay,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isMine) Color.White.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.primaryContainer
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isThisPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isThisPlaying) "Jeda pesan suara" else "Putar pesan suara",
                                    tint = if (isMine) Color.White else MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.GraphicEq,
                                        contentDescription = null,
                                        tint = textColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Voice Note • ${message.audioDurationSec} dtk",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = textColor
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    color = if (isMine) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.primary,
                                    trackColor = mutedColor.copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = DateTimeUtils.formatChatTime(message.createdAt?.toDate()?.time),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
