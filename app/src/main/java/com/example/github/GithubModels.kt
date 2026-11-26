package com.example.github

data class GithubUserInfo(
    val login: String,
    val name: String,
    val avatarUrl: String
)

data class GithubRepoSummary(
    val owner: String,
    val name: String,
    val fullName: String,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val archived: Boolean = false
)

data class GithubTreeEntry(val name: String, val sha: String, val mode: String, val type: String)

data class GithubTreeChange(
    val repoPath: String,
    val blobSha: String?, // null removes the entry from the new tree
    val mode: String = "100644",
    val type: String = "blob"
)
