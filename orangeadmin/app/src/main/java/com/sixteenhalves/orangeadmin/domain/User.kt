package com.sixteenhalves.orangeadmin.domain

import com.google.gson.annotations.SerializedName

data class User(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val role: String? = null,
    @SerializedName("branch_ids") val branchIds: List<String>? = null,
)

/** OWNER accounts can mutate; MANAGER accounts are read-only in the admin app. */
val User?.isOwner: Boolean
    get() = this?.role?.equals("OWNER", ignoreCase = true) == true
