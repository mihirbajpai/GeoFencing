package com.example.geofencing.shared.data

import com.example.geofencing.shared.AppConfig
import com.example.geofencing.shared.model.GeoFence
import com.example.geofencing.shared.model.Member
import com.example.geofencing.shared.model.MemberStatus
import com.example.geofencing.shared.model.Settings
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Reads and writes the shared fence, settings and member list in Firestore. */
@OptIn(ExperimentalTime::class)
internal object FirestoreRepository {
    private val db = Firebase.firestore
    private val fenceDoc = db.collection("config").document("fence")
    private val settingsDoc = db.collection("config").document("settings")
    private val members = db.collection("members")

    val fence: Flow<GeoFence?> = fenceDoc.snapshots
        .map { if (it.exists) it.data<GeoFence>() else null }
        .distinctUntilChanged()
        .retrying()

    val intervalSec: Flow<Int> = settingsDoc.snapshots
        .map { if (it.exists) it.data<Settings>().intervalSec else AppConfig.DEFAULT_INTERVAL_SEC }
        .distinctUntilChanged()
        .retrying()

    val memberList: Flow<List<Member>> = members.snapshots
        .map { snap -> snap.documents.map { it.data<Member>() }.sortedBy { it.name.lowercase() } }
        .retrying()

    suspend fun saveFence(lat: Double, lng: Double, radiusMeters: Double) =
        fenceDoc.set(GeoFence(lat, lng, radiusMeters, version = now()))

    suspend fun deleteFence() = fenceDoc.delete()

    suspend fun setIntervalSec(sec: Int) = settingsDoc.set(Settings(sec))

    suspend fun registerMember(id: String, name: String) =
        members.document(id).set(Profile(id, name), merge = true)

    // true only for the first OUTSIDE per fence version, so a retried report can't alert twice
    suspend fun reportStatus(
        id: String,
        name: String,
        status: MemberStatus,
        fenceVersion: Long
    ): Boolean {
        val ref = members.document(id)
        return db.runTransaction {
            val current = get(ref).takeIf { it.exists }?.data<Member>()
            val alreadyOut =
                current?.status == MemberStatus.OUTSIDE && current.fenceVersion == fenceVersion
            set(ref, Member(id, name, status, fenceVersion, now()))
            status == MemberStatus.OUTSIDE && !alreadyOut
        }
    }

    private fun <T> Flow<T>.retrying() = retryWhen { _, _ -> delay(3_000); true }

    private fun now() = Clock.System.now().toEpochMilliseconds()

    @Serializable
    private data class Profile(val id: String, val name: String)
}
