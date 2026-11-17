package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoBase64: String? = null,
    val activeTeamId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class JoinedTeam(
    @DocumentId val id: String = "",
    val teamId: String = "",
    val teamName: String = "",
    val teamCode: String = "",
    val role: String = "MEMBER",
    @ServerTimestamp val joinedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Team(
    @DocumentId val id: String = "",
    val teamCode: String = "",
    val name: String = "",
    val locationName: String = "Perumahan Bumi Papan Selaras",
    val ownerId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TeamMember(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val role: String = "MEMBER",
    val photoBase64: String? = null,
    @ServerTimestamp val joinedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DailyReport(
    @DocumentId val id: String = "",
    val teamId: String = "",
    val authorId: String = "",
    val memberName: String = "",
    val dayName: String = "",
    val dateLabel: String = "",
    val timeLabel: String = "",
    val yearMonth: String = "",
    val dayOfMonth: Long = 1L,
    val reportTimestampMillis: Long = 0L,
    val description: String = "",
    val photos: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class ChatMessageType {
    TEXT,
    IMAGE,
    AUDIO;

    companion object {
        fun fromRaw(raw: String?): ChatMessageType {
            return entries.firstOrNull { it.name == raw } ?: TEXT
        }
    }
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val teamId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoBase64: String? = null,
    val type: String = ChatMessageType.TEXT.name,
    val text: String = "",
    val imageBase64: String? = null,
    val audioBase64: String? = null,
    val audioDurationSec: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val messageType: ChatMessageType
        get() = ChatMessageType.fromRaw(type)
}

data class MemberMonthlySummary(
    val memberName: String,
    val reportCount: Int,
    val activeDaysCount: Int,
    val photoCount: Int,
    val reports: List<DailyReport>
)
