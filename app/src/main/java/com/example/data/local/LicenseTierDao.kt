package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LicenseTierDao {
    @Query("SELECT * FROM license_tiers ORDER BY valor ASC")
    fun getAllLicenseTiers(): Flow<List<LicenseTierEntity>>

    @Query("SELECT * FROM license_tiers ORDER BY valor ASC")
    suspend fun getAllLicenseTiersList(): List<LicenseTierEntity>

    @Query("SELECT * FROM license_tiers WHERE id = :id LIMIT 1")
    suspend fun getLicenseTierById(id: String): LicenseTierEntity?

    @Query("SELECT * FROM license_tiers WHERE UPPER(nome) = UPPER(:name) LIMIT 1")
    suspend fun getLicenseTierByName(name: String): LicenseTierEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(tier: LicenseTierEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tiers: List<LicenseTierEntity>)

    @Query("SELECT COUNT(*) FROM license_tiers")
    suspend fun count(): Int
}
