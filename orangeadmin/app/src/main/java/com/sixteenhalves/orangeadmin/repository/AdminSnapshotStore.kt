package com.sixteenhalves.orangeadmin.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sixteenhalves.orangeadmin.domain.Branch
import com.sixteenhalves.orangeadmin.domain.BranchDetail
import com.sixteenhalves.orangeadmin.domain.BranchOverview
import com.sixteenhalves.orangeadmin.domain.DashboardSummary
import com.sixteenhalves.orangeadmin.domain.InventoryProduct
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last-known-good copies of admin data, persisted so each screen can render
 * something useful when the network is down instead of an error page.
 */
@Singleton
class AdminSnapshotStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val gson = Gson()
        private val prefs = context.getSharedPreferences("admin_snapshots", Context.MODE_PRIVATE)

        fun saveBranches(branches: List<Branch>) = put(KEY_BRANCHES, gson.toJson(branches))

        fun readBranches(): List<Branch>? = read(KEY_BRANCHES, typeOf<List<Branch>>())

        fun saveOverviews(overviews: List<BranchOverview>) = put(KEY_OVERVIEWS, gson.toJson(overviews))

        fun readOverviews(): List<BranchOverview>? = read(KEY_OVERVIEWS, typeOf<List<BranchOverview>>())

        fun saveDashboard(summary: DashboardSummary) = put(KEY_DASHBOARD, gson.toJson(summary))

        fun readDashboard(): DashboardSummary? = read(KEY_DASHBOARD, typeOf<DashboardSummary>())

        fun saveInventory(products: List<InventoryProduct>) = put(KEY_INVENTORY, gson.toJson(products))

        fun readInventory(): List<InventoryProduct>? = read(KEY_INVENTORY, typeOf<List<InventoryProduct>>())

        fun saveBranchDetail(
            branchId: String,
            detail: BranchDetail,
        ) = put(detailKey(branchId), gson.toJson(detail))

        fun readBranchDetail(branchId: String): BranchDetail? = read(detailKey(branchId), typeOf<BranchDetail>())

        private fun detailKey(branchId: String): String = "$KEY_DETAIL_PREFIX$branchId"

        private fun put(
            key: String,
            json: String,
        ) {
            prefs.edit().putString(key, json).apply()
        }

        private fun <T> read(
            key: String,
            type: java.lang.reflect.Type,
        ): T? {
            val json = prefs.getString(key, null) ?: return null
            return runCatching { gson.fromJson<T>(json, type) }.getOrNull()
        }

        private inline fun <reified T> typeOf(): java.lang.reflect.Type = object : TypeToken<T>() {}.type

        private companion object {
            const val KEY_BRANCHES = "branches"
            const val KEY_OVERVIEWS = "overviews"
            const val KEY_DASHBOARD = "dashboard"
            const val KEY_INVENTORY = "inventory"
            const val KEY_DETAIL_PREFIX = "detail_"
        }
    }
