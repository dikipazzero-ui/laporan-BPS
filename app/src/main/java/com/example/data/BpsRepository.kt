package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale

class BpsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Silakan masuk dengan akun Google terlebih dahulu.")
    }

    private fun normalizeTeamCode(raw: String): String {
        return raw.trim().uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9_\\-]"), "")
    }

    private fun <T> wrapFirestoreCatch(
        path: String,
        op: OperationType,
        expectedUid: String,
        flow: Flow<T>
    ): Flow<T> = flow.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    // --- User & Team Observations ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Akses profil pengguna tidak valid." }
        val path = "users/$uid"
        emitAll(
            wrapFirestoreCatch(
                path = path,
                op = OperationType.GET,
                expectedUid = uid,
                flow = db.collection("users").document(uid)
                    .snapshots()
                    .map { snap ->
                        if (snap.exists()) {
                            snap.toObject(
                                UserProfile::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                        } else null
                    }
            )
        )
    }

    fun observeJoinedTeams(userId: String): Flow<List<JoinedTeam>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Akses daftar tim tidak valid." }
        val path = "users/$uid/joinedTeams"
        emitAll(
            wrapFirestoreCatch(
                path = path,
                op = OperationType.LIST,
                expectedUid = uid,
                flow = db.collection("users").document(uid)
                    .collection("joinedTeams")
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(
                            JoinedTeam::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ).sortedByDescending { it.joinedAt?.seconds ?: 0L }
                    }
            )
        )
    }

    fun observeTeam(teamId: String): Flow<Team?> = flow {
        val uid = requireUserId()
        val normalized = normalizeTeamCode(teamId)
        val path = "teams/$normalized"
        emitAll(
            wrapFirestoreCatch(
                path = path,
                op = OperationType.GET,
                expectedUid = uid,
                flow = db.collection("teams").document(normalized)
                    .snapshots()
                    .map { snap ->
                        if (snap.exists()) {
                            snap.toObject(
                                Team::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                        } else null
                    }
            )
        )
    }

    fun observeTeamMembers(teamId: String): Flow<List<TeamMember>> = flow {
        val uid = requireUserId()
        val normalized = normalizeTeamCode(teamId)
        val path = "teams/$normalized/members"
        emitAll(
            wrapFirestoreCatch(
                path = path,
                op = OperationType.LIST,
                expectedUid = uid,
                flow = db.collection("teams").document(normalized)
                    .collection("members")
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(
                            TeamMember::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ).sortedBy { it.displayName.lowercase(Locale.ROOT) }
                    }
            )
        )
    }

    fun observeReports(teamId: String): Flow<List<DailyReport>> = flow {
        val uid = requireUserId()
        val normalized = normalizeTeamCode(teamId)
        val path = "teams/$normalized/reports"
        emitAll(
            wrapFirestoreCatch(
                path = path,
                op = OperationType.LIST,
                expectedUid = uid,
                flow = db.collection("teams").document(normalized)
                    .collection("reports")
                    .orderBy("reportTimestampMillis", Query.Direction.DESCENDING)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(
                            DailyReport::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
            )
        )
    }

    fun observeMessages(teamId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val normalized = normalizeTeamCode(teamId)
        val path = "teams/$normalized/messages"
        emitAll(
            wrapFirestoreCatch(
                path = path,
                op = OperationType.LIST,
                expectedUid = uid,
                flow = db.collection("teams").document(normalized)
                    .collection("messages")
                    .orderBy("createdAt", Query.Direction.ASCENDING)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(
                            ChatMessage::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
            )
        )
    }

    // --- User & Team Mutations ---

    suspend fun ensureUserProfile(user: FirebaseUser): Result<UserProfile> {
        val path = "users/${user.uid}"
        return try {
            val uid = requireUserId()
            check(uid == user.uid) { "Session mismatch" }
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(userId = uid, displayName = user.displayName ?: "Anggota BPS")
                Result.success(existing)
            } else {
                val defaultName = (user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.email?.substringBefore("@")
                    ?: "Anggota BPS").take(60)
                val emailSafe = (user.email ?: "").take(120)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to defaultName,
                    "email" to emailSafe,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                val created = docRef.get().await().toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(userId = uid, displayName = defaultName, email = emailSafe)
                Result.success(created)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun createTeam(
        teamName: String,
        rawTeamCode: String,
        displayName: String,
        photoBase64: String?
    ): Result<Team> {
        val code = normalizeTeamCode(rawTeamCode)
        val path = "teams/$code"
        return try {
            val uid = requireUserId()
            require(code.length in 4..20 && code.matches(Regex("^[A-Z0-9_\\-]+$"))) {
                "Kode Tim harus 4–20 karakter huruf besar, angka, atau tanda hubung (-)."
            }
            val cleanTeamName = teamName.trim().take(80)
            require(cleanTeamName.length >= 2) { "Nama tim minimal 2 karakter." }
            val cleanDisplayName = displayName.trim().take(60)
            require(cleanDisplayName.isNotEmpty()) { "Nama tampilan anggota wajib diisi." }

            val teamRef = db.collection("teams").document(code)
            val existing = teamRef.get().await()
            require(!existing.exists()) {
                "Kode Tim '$code' sudah digunakan. Gunakan kode lain atau gabung ke tim tersebut."
            }

            // 1. Create Team first so member creation's exists() / owner check passes
            val teamPayload = mapOf(
                "teamCode" to code,
                "name" to cleanTeamName,
                "locationName" to "Perumahan Bumi Papan Selaras",
                "ownerId" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            teamRef.set(teamPayload).await()

            // 2. Register creator as ADMIN member
            val memberPayload = buildMap<String, Any> {
                put("userId", uid)
                put("displayName", cleanDisplayName)
                put("role", "ADMIN")
                if (!photoBase64.isNullOrBlank()) put("photoBase64", photoBase64)
                put("joinedAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            teamRef.collection("members").document(uid).set(memberPayload).await()

            // 3. Record in user's joinedTeams & set activeTeamId
            val joinedPayload = mapOf(
                "teamId" to code,
                "teamName" to cleanTeamName,
                "teamCode" to code,
                "role" to "ADMIN",
                "joinedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid)
                .collection("joinedTeams").document(code)
                .set(joinedPayload).await()

            val userUpdates = buildMap<String, Any> {
                put("displayName", cleanDisplayName)
                put("activeTeamId", code)
                if (!photoBase64.isNullOrBlank()) put("photoBase64", photoBase64)
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(uid).update(userUpdates).await()

            Result.success(
                Team(
                    id = code,
                    teamCode = code,
                    name = cleanTeamName,
                    locationName = "Perumahan Bumi Papan Selaras",
                    ownerId = uid
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun joinTeam(
        rawTeamCode: String,
        displayName: String,
        photoBase64: String?
    ): Result<Team> {
        val code = normalizeTeamCode(rawTeamCode)
        val path = "teams/$code"
        return try {
            val uid = requireUserId()
            require(code.length in 4..20 && code.matches(Regex("^[A-Z0-9_\\-]+$"))) {
                "Format Kode Tim tidak valid (4–20 karakter huruf/angka)."
            }
            val cleanDisplayName = displayName.trim().take(60)
            require(cleanDisplayName.isNotEmpty()) { "Nama tampilan wajib diisi." }

            val teamRef = db.collection("teams").document(code)
            val teamSnap = teamRef.get().await()
            require(teamSnap.exists()) {
                "Tim dengan kode '$code' tidak ditemukan. Periksa kembali kode tim Anda."
            }
            val team = teamSnap.toObject(
                Team::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: error("Gagal membaca data tim.")

            val memberRef = teamRef.collection("members").document(uid)
            val existingMember = memberRef.get().await()
            val role = if (team.ownerId == uid) "ADMIN" else "MEMBER"

            if (existingMember.exists()) {
                val updates = buildMap<String, Any> {
                    put("displayName", cleanDisplayName)
                    if (!photoBase64.isNullOrBlank()) put("photoBase64", photoBase64)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                memberRef.update(updates).await()
            } else {
                val memberPayload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", cleanDisplayName)
                    put("role", role)
                    if (!photoBase64.isNullOrBlank()) put("photoBase64", photoBase64)
                    put("joinedAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                memberRef.set(memberPayload).await()
            }

            val joinedRef = db.collection("users").document(uid)
                .collection("joinedTeams").document(code)
            val existingJoined = joinedRef.get().await()
            if (existingJoined.exists()) {
                joinedRef.update(mapOf("teamName" to team.name, "role" to role)).await()
            } else {
                joinedRef.set(
                    mapOf(
                        "teamId" to code,
                        "teamName" to team.name,
                        "teamCode" to code,
                        "role" to role,
                        "joinedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val userUpdates = buildMap<String, Any> {
                put("displayName", cleanDisplayName)
                put("activeTeamId", code)
                if (!photoBase64.isNullOrBlank()) put("photoBase64", photoBase64)
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(uid).update(userUpdates).await()

            Result.success(team)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun switchActiveTeam(teamId: String): Result<Unit> {
        val code = normalizeTeamCode(teamId)
        val path = "users/${auth.currentUser?.uid ?: "unauth"}"
        return try {
            val uid = requireUserId()
            require(code.length in 4..20) { "Kode Tim tidak valid." }
            db.collection("users").document(uid).update(
                mapOf(
                    "activeTeamId" to code,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMyProfile(
        teamId: String?,
        displayName: String,
        photoBase64: String?
    ): Result<Unit> {
        val cleanName = displayName.trim().take(60)
        val path = "users/${auth.currentUser?.uid ?: "unauth"}"
        return try {
            val uid = requireUserId()
            require(cleanName.isNotEmpty()) { "Nama tampilan tidak boleh kosong." }
            val userUpdates = buildMap<String, Any> {
                put("displayName", cleanName)
                if (!photoBase64.isNullOrBlank()) put("photoBase64", photoBase64)
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(uid).update(userUpdates).await()

            if (!teamId.isNullOrBlank()) {
                val code = normalizeTeamCode(teamId)
                val memberRef = db.collection("teams").document(code)
                    .collection("members").document(uid)
                if (memberRef.get().await().exists()) {
                    val memberUpdates = buildMap<String, Any> {
                        put("displayName", cleanName)
                        if (!photoBase64.isNullOrBlank()) put("photoBase64", photoBase64)
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    memberRef.update(memberUpdates).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Daily Reports CRUD ---

    suspend fun createReport(
        teamId: String,
        memberName: String,
        dayName: String,
        dateLabel: String,
        timeLabel: String,
        yearMonth: String,
        dayOfMonth: Int,
        reportTimestampMillis: Long,
        description: String,
        photos: List<String>
    ): Result<Unit> {
        val code = normalizeTeamCode(teamId)
        val path = "teams/$code/reports"
        return try {
            val uid = requireUserId()
            val cleanName = memberName.trim().take(60)
            val cleanDesc = description.trim().take(2000)
            require(cleanName.isNotEmpty()) { "Nama anggota wajib diisi." }
            require(cleanDesc.isNotEmpty()) { "Keterangan pelaporan wajib diisi." }
            require(photos.size <= 4) { "Maksimal 4 foto per laporan harian." }

            val docRef = db.collection("teams").document(code)
                .collection("reports").document()
            val payload = mapOf(
                "teamId" to code,
                "authorId" to uid,
                "memberName" to cleanName,
                "dayName" to dayName.trim().take(20),
                "dateLabel" to dateLabel.trim().take(40),
                "timeLabel" to timeLabel.trim().take(10),
                "yearMonth" to yearMonth,
                "dayOfMonth" to dayOfMonth.toLong(),
                "reportTimestampMillis" to reportTimestampMillis,
                "description" to cleanDesc,
                "photos" to photos.take(4),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateReport(
        teamId: String,
        reportId: String,
        memberName: String,
        dayName: String,
        dateLabel: String,
        timeLabel: String,
        yearMonth: String,
        dayOfMonth: Int,
        reportTimestampMillis: Long,
        description: String,
        photos: List<String>
    ): Result<Unit> {
        val code = normalizeTeamCode(teamId)
        val path = "teams/$code/reports/$reportId"
        return try {
            requireUserId()
            require(reportId.isNotBlank()) { "ID laporan tidak valid." }
            val cleanName = memberName.trim().take(60)
            val cleanDesc = description.trim().take(2000)
            require(cleanName.isNotEmpty()) { "Nama anggota wajib diisi." }
            require(cleanDesc.isNotEmpty()) { "Keterangan pelaporan wajib diisi." }
            require(photos.size <= 4) { "Maksimal 4 foto per laporan harian." }

            db.collection("teams").document(code)
                .collection("reports").document(reportId)
                .update(
                    mapOf(
                        "memberName" to cleanName,
                        "dayName" to dayName.trim().take(20),
                        "dateLabel" to dateLabel.trim().take(40),
                        "timeLabel" to timeLabel.trim().take(10),
                        "yearMonth" to yearMonth,
                        "dayOfMonth" to dayOfMonth.toLong(),
                        "reportTimestampMillis" to reportTimestampMillis,
                        "description" to cleanDesc,
                        "photos" to photos.take(4),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteReport(teamId: String, reportId: String): Result<Unit> {
        val code = normalizeTeamCode(teamId)
        val path = "teams/$code/reports/$reportId"
        return try {
            requireUserId()
            require(reportId.isNotBlank()) { "ID laporan tidak valid." }
            db.collection("teams").document(code)
                .collection("reports").document(reportId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Live Chat Mutations ---

    suspend fun sendTextMessage(
        teamId: String,
        senderName: String,
        senderPhotoBase64: String?,
        text: String
    ): Result<Unit> {
        val code = normalizeTeamCode(teamId)
        val path = "teams/$code/messages"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(1500)
            require(cleanText.isNotEmpty()) { "Pesan tidak boleh kosong." }
            val payload = buildMap<String, Any> {
                put("teamId", code)
                put("senderId", uid)
                put("senderName", senderName.trim().take(60).ifEmpty { "Anggota" })
                if (!senderPhotoBase64.isNullOrBlank()) put("senderPhotoBase64", senderPhotoBase64)
                put("type", ChatMessageType.TEXT.name)
                put("text", cleanText)
                put("audioDurationSec", 0L)
                put("createdAt", FieldValue.serverTimestamp())
            }
            db.collection("teams").document(code)
                .collection("messages").document()
                .set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendImageMessage(
        teamId: String,
        senderName: String,
        senderPhotoBase64: String?,
        imageBase64: String,
        caption: String
    ): Result<Unit> {
        val code = normalizeTeamCode(teamId)
        val path = "teams/$code/messages"
        return try {
            val uid = requireUserId()
            require(imageBase64.length >= 10) { "Foto tidak valid." }
            val payload = buildMap<String, Any> {
                put("teamId", code)
                put("senderId", uid)
                put("senderName", senderName.trim().take(60).ifEmpty { "Anggota" })
                if (!senderPhotoBase64.isNullOrBlank()) put("senderPhotoBase64", senderPhotoBase64)
                put("type", ChatMessageType.IMAGE.name)
                put("text", caption.trim().take(1500))
                put("imageBase64", imageBase64)
                put("audioDurationSec", 0L)
                put("createdAt", FieldValue.serverTimestamp())
            }
            db.collection("teams").document(code)
                .collection("messages").document()
                .set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendAudioMessage(
        teamId: String,
        senderName: String,
        senderPhotoBase64: String?,
        audioBase64: String,
        durationSec: Int
    ): Result<Unit> {
        val code = normalizeTeamCode(teamId)
        val path = "teams/$code/messages"
        return try {
            val uid = requireUserId()
            require(audioBase64.length >= 10) { "Rekaman suara terlalu singkat atau kosong." }
            val boundedDuration = durationSec.coerceIn(1, 120).toLong()
            val payload = buildMap<String, Any> {
                put("teamId", code)
                put("senderId", uid)
                put("senderName", senderName.trim().take(60).ifEmpty { "Anggota" })
                if (!senderPhotoBase64.isNullOrBlank()) put("senderPhotoBase64", senderPhotoBase64)
                put("type", ChatMessageType.AUDIO.name)
                put("text", "Pesan suara ($boundedDuration dtk)")
                put("audioBase64", audioBase64)
                put("audioDurationSec", boundedDuration)
                put("createdAt", FieldValue.serverTimestamp())
            }
            db.collection("teams").document(code)
                .collection("messages").document()
                .set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(teamId: String, messageId: String): Result<Unit> {
        val code = normalizeTeamCode(teamId)
        val path = "teams/$code/messages/$messageId"
        return try {
            requireUserId()
            db.collection("teams").document(code)
                .collection("messages").document(messageId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
