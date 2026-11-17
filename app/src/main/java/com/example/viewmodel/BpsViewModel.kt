package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BpsRepository
import com.example.data.ChatMessage
import com.example.data.DailyReport
import com.example.data.JoinedTeam
import com.example.data.Team
import com.example.data.TeamMember
import com.example.data.UiState
import com.example.data.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class BpsViewModel(
    private val repository: BpsRepository,
    val currentUserId: String
) : ViewModel() {

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .toUiState("Gagal memuat profil pengguna")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val joinedTeamsState: StateFlow<UiState<List<JoinedTeam>>> =
        repository.observeJoinedTeams(currentUserId)
            .toUiState("Gagal memuat daftar tim")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    private val activeTeamIdFlow: Flow<String?> = userProfileState
        .map { state ->
            when (state) {
                is UiState.Success -> state.data?.activeTeamId?.takeIf { it.isNotBlank() }
                else -> null
            }
        }
        .distinctUntilChanged()

    val activeTeamState: StateFlow<UiState<Team?>> = activeTeamIdFlow
        .flatMapLatest { teamId ->
            if (teamId == null) flowOf(UiState.Success(null))
            else repository.observeTeam(teamId).toUiState("Gagal memuat data tim")
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val teamMembersState: StateFlow<UiState<List<TeamMember>>> = activeTeamIdFlow
        .flatMapLatest { teamId ->
            if (teamId == null) flowOf(UiState.Success(emptyList()))
            else repository.observeTeamMembers(teamId).toUiState("Gagal memuat daftar anggota")
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Success(emptyList())
        )

    val reportsState: StateFlow<UiState<List<DailyReport>>> = activeTeamIdFlow
        .flatMapLatest { teamId ->
            if (teamId == null) flowOf(UiState.Success(emptyList()))
            else repository.observeReports(teamId).toUiState("Gagal memuat laporan harian")
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val messagesState: StateFlow<UiState<List<ChatMessage>>> = activeTeamIdFlow
        .flatMapLatest { teamId ->
            if (teamId == null) flowOf(UiState.Success(emptyList()))
            else repository.observeMessages(teamId).toUiState("Gagal memuat pesan obrolan")
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    init {
        viewModelScope.launch {
            val user = Firebase.auth.currentUser
            if (user != null && user.uid == currentUserId) {
                repository.ensureUserProfile(user).onFailure { err ->
                    _events.tryEmit(err.localizedMessage ?: "Gagal menyinkronkan profil akun")
                }
            }
        }
    }

    private fun <T> Flow<T>.toUiState(fallbackMessage: String): Flow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, fallbackMessage, rootError)
                emit(UiState.Error(rootError.localizedMessage ?: fallbackMessage))
            }

    fun createTeam(
        teamName: String,
        teamCode: String,
        displayName: String,
        photoBase64: String?,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createTeam(teamName, teamCode, displayName, photoBase64)
            _isBusy.value = false
            res.onSuccess {
                _events.tryEmit("Tim '${it.name}' berhasil dibuat (Kode: ${it.teamCode})")
                onSuccess()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Gagal membuat tim")
            }
        }
    }

    fun joinTeam(
        teamCode: String,
        displayName: String,
        photoBase64: String?,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.joinTeam(teamCode, displayName, photoBase64)
            _isBusy.value = false
            res.onSuccess {
                _events.tryEmit("Berhasil bergabung ke tim '${it.name}'")
                onSuccess()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Gagal bergabung ke tim")
            }
        }
    }

    fun switchActiveTeam(teamId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.switchActiveTeam(teamId)
            _isBusy.value = false
            res.onSuccess {
                onSuccess()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Gagal mengganti tim aktif")
            }
        }
    }

    fun updateProfile(
        teamId: String?,
        displayName: String,
        photoBase64: String?,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateMyProfile(teamId, displayName, photoBase64)
            _isBusy.value = false
            res.onSuccess {
                _events.tryEmit("Profil berhasil diperbarui")
                onSuccess()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Gagal memperbarui profil")
            }
        }
    }

    fun saveDailyReport(
        teamId: String,
        editingReportId: String?,
        memberName: String,
        dayName: String,
        dateLabel: String,
        timeLabel: String,
        yearMonth: String,
        dayOfMonth: Int,
        reportTimestampMillis: Long,
        description: String,
        photos: List<String>,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = if (editingReportId.isNullOrBlank()) {
                repository.createReport(
                    teamId, memberName, dayName, dateLabel, timeLabel,
                    yearMonth, dayOfMonth, reportTimestampMillis, description, photos
                )
            } else {
                repository.updateReport(
                    teamId, editingReportId, memberName, dayName, dateLabel, timeLabel,
                    yearMonth, dayOfMonth, reportTimestampMillis, description, photos
                )
            }
            _isBusy.value = false
            res.onSuccess {
                _events.tryEmit(
                    if (editingReportId.isNullOrBlank()) "Laporan harian tersimpan & tersinkron"
                    else "Perubahan laporan tersimpan"
                )
                onSuccess()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Gagal menyimpan laporan")
            }
        }
    }

    fun deleteDailyReport(teamId: String, reportId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.deleteReport(teamId, reportId)
            _isBusy.value = false
            res.onSuccess {
                _events.tryEmit("Laporan dihapus")
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Gagal menghapus laporan")
            }
        }
    }

    fun sendTextMessage(
        teamId: String,
        senderName: String,
        senderPhotoBase64: String?,
        text: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.sendTextMessage(teamId, senderName, senderPhotoBase64, text)
            res.onSuccess {
                onSuccess()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Gagal mengirim pesan")
            }
        }
    }

    fun sendImageMessage(
        teamId: String,
        senderName: String,
        senderPhotoBase64: String?,
        imageBase64: String,
        caption: String = ""
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.sendImageMessage(teamId, senderName, senderPhotoBase64, imageBase64, caption)
            _isBusy.value = false
            res.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Gagal mengirim foto")
            }
        }
    }

    fun sendAudioMessage(
        teamId: String,
        senderName: String,
        senderPhotoBase64: String?,
        audioBase64: String,
        durationSec: Int
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.sendAudioMessage(teamId, senderName, senderPhotoBase64, audioBase64, durationSec)
            _isBusy.value = false
            res.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Gagal mengirim pesan suara")
            }
        }
    }

    fun deleteMessage(teamId: String, messageId: String) {
        viewModelScope.launch {
            val res = repository.deleteMessage(teamId, messageId)
            res.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Gagal menghapus pesan")
            }
        }
    }

    private companion object {
        const val TAG = "BpsViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
