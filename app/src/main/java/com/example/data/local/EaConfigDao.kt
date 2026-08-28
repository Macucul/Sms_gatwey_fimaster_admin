package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EaConfigDao {
    @Query("SELECT * FROM ea_configs WHERE mt5IdConta = :mt5 LIMIT 1")
    fun getEaConfigByMt5(mt5: String): Flow<EaConfigEntity?>

    @Query("SELECT * FROM ea_configs WHERE mt5IdConta = :mt5 LIMIT 1")
    suspend fun getEaConfigByMt5Suspended(mt5: String): EaConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEaConfig(config: EaConfigEntity)

    @Query("DELETE FROM ea_configs WHERE mt5IdConta = :mt5")
    suspend fun deleteEaConfig(mt5: String)
}
