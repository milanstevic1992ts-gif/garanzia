package com.milanstevic.garanzia.data

import android.content.Context
import androidx.room.Room
import com.milanstevic.garanzia.data.local.GaranziaDatabase
import com.milanstevic.garanzia.data.local.ReceiptDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): GaranziaDatabase =
        Room.databaseBuilder(
            context,
            GaranziaDatabase::class.java,
            "garanzia.db",
        ).build()

    @Provides
    fun provideReceiptDao(
        database: GaranziaDatabase,
    ): ReceiptDao = database.receiptDao()
}
