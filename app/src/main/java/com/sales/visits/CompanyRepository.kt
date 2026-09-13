package com.sales.visits

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * The company / team membership layer (Phase 4 foundation).
 *
 * Data model in Firestore:
 *  - companies/{companyId}                     { name, ownerUid, inviteCode, createdAt }
 *  - companies/{companyId}/members/{uid}       { name, email, role, joinedAt }
 *  - inviteCodes/{code}                        { companyId }
 *  - users/{uid}/private/membership            { companyId, role }   (the user's own pointer)
 *
 * Access is enforced by Firestore security rules (see firestore.rules) — NOT by hiding UI. A user
 * can only read a company they are a member of, only managers can change roles, and each account
 * can only touch its own `users/{uid}` subtree.
 *
 * NOTE: This is the membership foundation. Sharing customers/visits across the team and the manager
 * dashboard require moving that data into company-scoped collections (a following increment) and
 * live multi-device testing against the deployed rules.
 */
class CompanyRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    var companyId by mutableStateOf<String?>(null); private set
    var companyName by mutableStateOf(""); private set
    var inviteCode by mutableStateOf(""); private set
    var teamGoal by mutableStateOf(0); private set   // target completed assignments (0 = none)
    var role by mutableStateOf<TeamRole?>(null); private set
    var members by mutableStateOf<List<TeamMember>>(emptyList()); private set
    var assignments by mutableStateOf<List<Assignment>>(emptyList()); private set
    var teamOpps by mutableStateOf<List<TeamOpp>>(emptyList()); private set   // shared company pipeline (6.2)
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    private var membershipReg: ListenerRegistration? = null
    private var companyReg: ListenerRegistration? = null
    private var membersReg: ListenerRegistration? = null
    private var assignmentsReg: ListenerRegistration? = null
    private var teamOppsReg: ListenerRegistration? = null

    private val authListener = FirebaseAuth.AuthStateListener { start() }

    init {
        auth.addAuthStateListener(authListener)
        start()
    }

    val isManager: Boolean get() = role == TeamRole.MANAGER

    private fun start() {
        val uid = auth.currentUser?.uid
        detachCompany()
        membershipReg?.remove(); membershipReg = null
        if (uid == null) { reset(); return }
        membershipReg = db.collection("users").document(uid)
            .collection("private").document("membership")
            .addSnapshotListener { snap, _ ->
                val cid = snap?.getString("companyId")
                if (cid.isNullOrBlank()) { reset() } else attachCompany(cid, uid)
            }
    }

    private fun attachCompany(cid: String, uid: String) {
        if (companyId == cid && companyReg != null) return
        detachCompany()
        companyId = cid
        companyReg = db.collection("companies").document(cid)
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    companyName = snap.getString("name").orEmpty()
                    inviteCode = snap.getString("inviteCode").orEmpty()
                    teamGoal = (snap.getLong("teamGoal") ?: 0L).toInt()
                }
            }
        membersReg = db.collection("companies").document(cid).collection("members")
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.map {
                    TeamMember(
                        uid = it.id,
                        name = it.getString("name").orEmpty(),
                        email = it.getString("email").orEmpty(),
                        role = TeamRole.from(it.getString("role")),
                    )
                }.orEmpty().sortedWith(compareByDescending<TeamMember> { it.role == TeamRole.MANAGER }.thenBy { it.name })
                members = list
                // Reconcile: if I'm no longer in the members list, I was removed → drop membership.
                if (list.isNotEmpty() && list.none { it.uid == uid }) leaveLocalOnly(uid)
                else role = list.firstOrNull { it.uid == uid }?.role ?: role
            }
        assignmentsReg = db.collection("companies").document(cid).collection("assignments")
            .addSnapshotListener { snap, _ ->
                assignments = snap?.documents?.map {
                    Assignment(
                        id = it.id,
                        customerName = it.getString("customerName").orEmpty(),
                        phone = it.getString("phone").orEmpty(),
                        address = it.getString("address").orEmpty(),
                        note = it.getString("note").orEmpty(),
                        assignedTo = it.getString("assignedTo").orEmpty(),
                        assignedToName = it.getString("assignedToName").orEmpty(),
                        done = it.getBoolean("done") ?: false,
                    )
                }.orEmpty().sortedWith(compareBy({ it.done }, { it.customerName }))
            }
        teamOppsReg = db.collection("companies").document(cid).collection("opportunities")
            .addSnapshotListener { snap, _ ->
                teamOpps = snap?.documents?.map {
                    TeamOpp(
                        id = it.id,
                        title = it.getString("title").orEmpty(),
                        customerName = it.getString("customerName").orEmpty(),
                        value = it.getDouble("value") ?: 0.0,
                        currency = it.getString("currency").orEmpty(),
                        stage = it.getString("stage").orEmpty().ifBlank { "NEW" },
                        ownerUid = it.getString("ownerUid").orEmpty(),
                        ownerName = it.getString("ownerName").orEmpty(),
                    )
                }.orEmpty().sortedByDescending { it.value }
            }
    }

    private fun detachCompany() {
        companyReg?.remove(); companyReg = null
        membersReg?.remove(); membersReg = null
        assignmentsReg?.remove(); assignmentsReg = null
        teamOppsReg?.remove(); teamOppsReg = null
    }

    private fun reset() {
        detachCompany()
        companyId = null; companyName = ""; inviteCode = ""; role = null
        members = emptyList(); assignments = emptyList(); teamOpps = emptyList()
    }

    /** Share an opportunity to the whole team (plan 6.2). Tagged with the sharer as ownerUid; the
     *  backend rules let only that owner (or a manager) edit/delete it. */
    fun shareOpportunity(title: String, customerName: String, value: Double, currency: String) {
        val cid = companyId ?: return
        val uid = auth.currentUser?.uid ?: return
        if (title.isBlank()) return
        db.collection("companies").document(cid).collection("opportunities").add(mapOf(
            "title" to title.trim(), "customerName" to customerName.trim(), "value" to value, "currency" to currency.trim(),
            "stage" to "NEW", "ownerUid" to uid, "ownerName" to (members.firstOrNull { it.uid == uid }?.name ?: ""),
            "createdAt" to FieldValue.serverTimestamp(),
        ))
    }

    /** Delete a shared opportunity — the backend allows only its owner or a manager. */
    fun deleteTeamOpportunity(id: String) {
        val cid = companyId ?: return
        db.collection("companies").document(cid).collection("opportunities").document(id).delete()
    }

    /** My own assigned work (what a rep sees on their plate). */
    fun myAssignments(): List<Assignment> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        return assignments.filter { it.assignedTo == uid }
    }

    fun updateTeamGoal(n: Int) {
        val cid = companyId ?: return
        if (!isManager) return
        db.collection("companies").document(cid).update("teamGoal", n.coerceIn(0, 9999).toLong())
    }

    /** Per-rep assignment stats for the manager dashboard: rep uid → (assigned, done). */
    fun repStats(): List<Triple<String, Int, Int>> = members.map { m ->
        val mine = assignments.filter { it.assignedTo == m.uid }
        Triple(m.name.ifBlank { m.email.substringBefore('@') }, mine.size, mine.count { it.done })
    }

    fun addAssignment(customerName: String, phone: String, address: String, note: String, repUid: String, repName: String) {
        val cid = companyId ?: return
        if (!isManager || customerName.isBlank()) return
        db.collection("companies").document(cid).collection("assignments").add(mapOf(
            "customerName" to customerName.trim(), "phone" to phone.trim(), "address" to address.trim(),
            "note" to note.trim(), "assignedTo" to repUid, "assignedToName" to repName,
            "done" to false, "createdBy" to (auth.currentUser?.uid ?: ""), "createdAt" to FieldValue.serverTimestamp(),
        ))
    }

    fun setAssignmentDone(id: String, done: Boolean) {
        val cid = companyId ?: return
        db.collection("companies").document(cid).collection("assignments").document(id).update("done", done)
    }

    fun reassign(id: String, repUid: String, repName: String) {
        val cid = companyId ?: return
        if (!isManager) return
        db.collection("companies").document(cid).collection("assignments").document(id)
            .update(mapOf("assignedTo" to repUid, "assignedToName" to repName))
    }

    fun deleteAssignment(id: String) {
        val cid = companyId ?: return
        if (!isManager) return
        db.collection("companies").document(cid).collection("assignments").document(id).delete()
    }

    private fun randomCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { alphabet.random() }.joinToString("")
    }

    fun createCompany(name: String, displayName: String, email: String) {
        val uid = auth.currentUser?.uid ?: return
        if (name.isBlank() || busy) return
        busy = true; error = null
        val cid = db.collection("companies").document().id
        val code = randomCode()
        val batch = db.batch()
        batch.set(db.collection("companies").document(cid), mapOf(
            "name" to name.trim(), "ownerUid" to uid, "inviteCode" to code, "createdAt" to FieldValue.serverTimestamp(),
        ))
        batch.set(db.collection("companies").document(cid).collection("members").document(uid), mapOf(
            "name" to displayName, "email" to email, "role" to "MANAGER", "joinedAt" to FieldValue.serverTimestamp(),
        ))
        batch.set(db.collection("inviteCodes").document(code), mapOf("companyId" to cid))
        batch.set(db.collection("users").document(uid).collection("private").document("membership"), mapOf(
            "companyId" to cid, "role" to "MANAGER",
        ))
        batch.commit()
            .addOnSuccessListener { busy = false }
            .addOnFailureListener { busy = false; error = it.message }
    }

    fun joinByCode(code: String, displayName: String, email: String) {
        val uid = auth.currentUser?.uid ?: return
        val c = code.trim().uppercase()
        if (c.isBlank() || busy) return
        busy = true; error = null
        db.collection("inviteCodes").document(c).get()
            .addOnSuccessListener { doc ->
                val cid = doc.getString("companyId")
                if (cid.isNullOrBlank()) { busy = false; error = "invalid_code"; return@addOnSuccessListener }
                val batch = db.batch()
                batch.set(db.collection("companies").document(cid).collection("members").document(uid), mapOf(
                    // inviteCode is validated by the backend rules against the company's current code,
                    // so a rep can only join with a real, un-rotated invite.
                    "name" to displayName, "email" to email, "role" to "REP", "inviteCode" to c,
                    "joinedAt" to FieldValue.serverTimestamp(),
                ))
                batch.set(db.collection("users").document(uid).collection("private").document("membership"), mapOf(
                    "companyId" to cid, "role" to "REP",
                ))
                batch.commit()
                    .addOnSuccessListener { busy = false }
                    .addOnFailureListener { busy = false; error = it.message }
            }
            .addOnFailureListener { busy = false; error = it.message }
    }

    fun setMemberRole(memberUid: String, newRole: TeamRole) {
        val cid = companyId ?: return
        if (!isManager) return
        // Don't demote the last remaining manager — the company would be left with no one who can
        // manage it. (Client guard; true backend enforcement of this needs a Cloud Function.)
        if (newRole == TeamRole.REP) {
            val target = members.firstOrNull { it.uid == memberUid }
            val managerCount = members.count { it.role == TeamRole.MANAGER }
            if (target?.role == TeamRole.MANAGER && managerCount <= 1) { error = "last_manager"; return }
        }
        error = null
        db.collection("companies").document(cid).collection("members").document(memberUid)
            .update("role", newRole.name)
    }

    fun leave() {
        val uid = auth.currentUser?.uid ?: return
        val cid = companyId ?: return
        // The last manager can't abandon a team that still has members — transfer management first.
        val me = members.firstOrNull { it.uid == uid }
        val managerCount = members.count { it.role == TeamRole.MANAGER }
        if (me?.role == TeamRole.MANAGER && managerCount <= 1 && members.size > 1) { error = "last_manager"; return }
        error = null
        db.collection("companies").document(cid).collection("members").document(uid).delete()
        db.collection("users").document(uid).collection("private").document("membership").delete()
        reset()
    }

    private fun leaveLocalOnly(uid: String) {
        db.collection("users").document(uid).collection("private").document("membership").delete()
        reset()
    }

    fun close() {
        auth.removeAuthStateListener(authListener)
        detachCompany()
        membershipReg?.remove(); membershipReg = null
    }
}
