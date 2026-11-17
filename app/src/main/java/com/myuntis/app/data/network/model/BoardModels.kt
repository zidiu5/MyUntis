package com.myuntis.app.data.network.model

// Every field has a default → Kotlin generates a no-arg constructor and Gson
// applies the defaults when a field is missing (e.g. in deleted posts).

data class BoardMember(val id: String = "", val nickname: String = "")

data class BoardInfo(
    val schoolId: String = "",
    val schoolName: String = "",
    val classId: Int = 0,
    val className: String = "",
    val schoolYear: String? = null
)

data class BoardAuthResponse(
    val token: String = "",
    val expiresAt: String = "",
    val member: BoardMember = BoardMember(),
    val boards: List<BoardInfo> = emptyList()
)

data class BoardStats(
    val activeMembers: Int = 0,
    val usedBytes: Long = 0,
    val quotaBytes: Long = 0
)

data class BoardAuthor(val id: String = "", val nickname: String = "", val mine: Boolean = false)

data class BoardAttachment(
    val id: String = "", val name: String = "", val mime: String = "", val size: Long = 0
)

data class DeleteVotes(val count: Int = 0, val needed: Int = 0, val mine: Boolean = false)

data class BoardPost(
    val id: String = "",
    val type: String = "INFO",            // HOMEWORK | EXAM | INFO | FILE
    val subject: String? = null,
    val title: String? = null,
    val body: String? = null,
    val dueDate: String? = null,          // YYYY-MM-DD
    val hidden: Boolean = false,
    val deleted: Boolean = false,
    val openEdit: Boolean = false,
    val canEdit: Boolean = false,
    val lastEditedBy: String? = null,
    val deleteVotes: DeleteVotes? = null,
    val author: BoardAuthor? = null,
    val attachments: List<BoardAttachment> = emptyList(),
    val createdAt: String = "",
    val updatedAt: String = ""
)

data class BoardPostsResponse(
    val posts: List<BoardPost> = emptyList(),
    val hasMore: Boolean = false,
    val serverTime: String = "",
    val board: BoardStats? = null
)

// ── Stored on the phone ───────────────────────────────────────
data class BoardSession(
    val token: String = "",
    val expiresAt: String = "",
    val memberId: String = "",
    val nickname: String = "",
    val nicknameConfirmed: Boolean = false,
    val schoolId: String = "",
    val schoolName: String = "",
    val classId: Int = 0,
    val className: String = ""
)

data class BoardCache(
    val schoolId: String = "",
    val classId: Int = 0,
    val serverTime: String = "",
    val posts: List<BoardPost> = emptyList(),
    val stats: BoardStats? = null
)

data class BoardUploadResponse(
    val fileId: String = "", val name: String = "", val mime: String = "", val size: Long = 0
)

data class VoteResult(val count: Int = 0, val needed: Int = 0, val deleted: Boolean = false)

data class StorageItem(
    val postId: String = "",
    val title: String? = null,
    val subject: String? = null,
    val size: Long = 0,
    val createdAt: String = "",
    val author: BoardAuthor? = null,
    val deleteVotes: DeleteVotes? = null
)

data class StorageResponse(
    val usedBytes: Long = 0,
    val quotaBytes: Long = 0,
    val items: List<StorageItem> = emptyList()
)