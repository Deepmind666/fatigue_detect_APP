package com.example.juicemachine.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.juicemachine.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Recipe::class], version = 11, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // 在数据库创建时插入初始数据
            db.execSQL("INSERT INTO recipes (id, name, water, juice, price, remainWeight, juiceChannel, imageUri) VALUES (1, '茉莉雪芽', 105, 175, 8, 1000, 1, NULL)")
            db.execSQL("INSERT INTO recipes (id, name, water, juice, price, remainWeight, juiceChannel, imageUri) VALUES (2, '柳橙百香', 180, 100, 9, 1000, 2, NULL)")
            db.execSQL("INSERT INTO recipes (id, name, water, juice, price, remainWeight, juiceChannel, imageUri) VALUES (3, '满杯桑葚', 130, 150, 10, 1000, 3, NULL)")
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "juice_machine_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(AppDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}