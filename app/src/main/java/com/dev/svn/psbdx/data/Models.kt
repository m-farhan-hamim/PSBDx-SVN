package com.dev.svn.psbdx.data

data class SvnRepo(
    val id: String,
    val alias: String,
    val url: String,
    val username: String,
    val password: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
