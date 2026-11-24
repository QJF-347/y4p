package com.sixteenhalves.orangeadmin.repository

import com.sixteenhalves.orangeadmin.domain.User
import com.sixteenhalves.orangeadmin.viewmodels.AuthLoginFlowData

interface UserRepository {
    suspend fun getUser(): Result<User?>

    suspend fun createUser(
        name: String,
        email: String,
        password: String,
        role: String,
        branchIds: List<String> = emptyList(),
    ): Result<User?>

    suspend fun loginUser(request: AuthLoginFlowData): Result<User?>

    suspend fun logoutUser(refreshToken: String): Result<Unit>

    suspend fun deleteAccount(password: String): Result<Unit>
}
