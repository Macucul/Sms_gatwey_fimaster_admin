package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class, 
        AuditLogEntity::class, 
        SmsLogEntity::class, 
        PendingPaymentEntity::class, 
        RefundEntity::class,
        EaConfigEntity::class,
        LicenseTierEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class SmsGatewayDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun smsLogDao(): SmsLogDao
    abstract fun pendingPaymentDao(): PendingPaymentDao
    abstract fun refundDao(): RefundDao
    abstract fun eaConfigDao(): EaConfigDao
    abstract fun licenseTierDao(): LicenseTierDao

    companion object {
        @Volatile
        private var INSTANCE: SmsGatewayDatabase? = null

        fun getDatabase(context: Context): SmsGatewayDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmsGatewayDatabase::class.java,
                    "sms_gateway_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
