package com.sixteenhalves.orangeadmin.repository

import com.sixteenhalves.orangeadmin.domain.User
import com.sixteenhalves.orangeadmin.ntwk.CreateUserRequest
import com.sixteenhalves.orangeadmin.ntwk.DeleteAccountRequest
import com.sixteenhalves.orangeadmin.ntwk.NetworkService
import com.sixteenhalves.orangeadmin.ntwk.RefreshRequest
import com.sixteenhalves.orangeadmin.viewmodels.AuthLoginFlowData
import javax.inject.Inject

class UserRepositoryImpl
    @Inject
    constructor(
        private val networkService: NetworkService,
    ) : UserRepository {
        override suspend fun createUser(
            name: String,
            email: String,
            password: String,
            role: String,
            branchIds: List<String>,
        ): Result<User?> =
            try {
                val response =
                    networkService.createUser(
                        CreateUserRequest(
                            name = name,
                            email = email,
                            password = password,
                            role = role,
                            branchIds = branchIds,
                        ),
                    )
                if (response.isSuccessful) {
                    Result.success(response.body()?.user)
                } else {
                    Result.failure(Exception("Network error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }

        override suspend fun getUser(): Result<User?> =
            try {
                val response = networkService.getUser()
                if (response.isSuccessful) {
                    Result.success(response.body()?.user)
                } else {
                    Result.failure(Exception("Network error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }

        override suspend fun loginUser(request: AuthLoginFlowData): Result<User?> =
            try {
                val response = networkService.loginUser(request)
                if (response.isSuccessful) {
                    Result.success(response.body()?.user)
                } else {
                    Result.failure(Exception("Network error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }

        override suspend fun logoutUser(refreshToken: String): Result<Unit> =
            try {
                val response = networkService.logoutUser(RefreshRequest(refreshToken))
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Network error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }

        override suspend fun deleteAccount(password: String): Result<Unit> =
            try {
                val response = networkService.deleteUser(DeleteAccountRequest(password))
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Network error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
    }
