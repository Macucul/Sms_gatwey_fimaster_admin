package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY timestamp DESC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY timestamp DESC")
    suspend fun getAllUsersList(): List<UserEntity>

    @Query("SELECT * FROM users WHERE nome LIKE :searchQuery OR telefone LIKE :searchQuery OR mt5IdConta LIKE :searchQuery OR idTransacao LIKE :searchQuery OR idUsuario LIKE :searchQuery ORDER BY timestamp DESC")
    fun searchUsers(searchQuery: String): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'")
    suspend fun getUnsyncedUsers(): List<UserEntity>

    @Query("SELECT COUNT(*) FROM users WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'")
    fun getUnsyncedUsersCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM users")
    fun getUserCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM users WHERE status = 'APROVADO' OR status = 'ATIVO'")
    fun getApprovedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM users WHERE status = 'AGUARDANDO_ATIVACAO' OR status = 'PENDENTE'")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM users WHERE status = 'REJEITADO'")
    fun getRejectedCountFlow(): Flow<Int>

    @Query("SELECT * FROM users ORDER BY timestamp DESC LIMIT 1")
    fun getLastUserFlow(): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE idUsuario = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE telefone = :phone LIMIT 1")
    suspend fun getUserByPhone(phone: String): UserEntity?

    @Query("SELECT * FROM users WHERE idTransacao = :txId LIMIT 1")
    suspend fun getUserByTransactionId(txId: String): UserEntity?

    @Query("SELECT * FROM users WHERE mt5IdConta = :mt5 LIMIT 1")
    suspend fun getUserByMt5(mt5: String): UserEntity?

    @Query("DELETE FROM users")
    suspend fun clearAllUsers()
}
