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

data class GithubTreeBlob(
    val repoPath: String,
    val relativePath: String,
    val sha: String,
    val size: Long
)

data class GithubTreeChange(
    val repoPath: String,
    val blobSha: String? // null means delete file in Git tree
)
