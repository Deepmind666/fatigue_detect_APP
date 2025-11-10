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

@Database(entities = [Recipe::class, Order::class], version = 18, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao

    abstract fun orderDao(): OrderDao

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // 在数据库创建时插入初始数据（更新 juiceType 文案）
            // 默认将水流速与果汁流速设置为60，确保设备指令包含有效速度
            db.execSQL("INSERT INTO recipes (id, name, water, juice, price, defaultRemainingWeight, currentRemainingWeight, juiceChannel, imageUri, juiceType, waterSpeed, juiceSpeed) VALUES (1, '茉莉雪芽', 105, 175, 8, 1000, 1000, 1, NULL, '牛奶绿茶', 60, 60)")
            db.execSQL("INSERT INTO recipes (id, name, water, juice, price, defaultRemainingWeight, currentRemainingWeight, juiceChannel, imageUri, juiceType, waterSpeed, juiceSpeed) VALUES (2, '柳橙百香', 180, 100, 9, 1000, 1000, 2, NULL, '橙汁百香果汁', 60, 60)")
            db.execSQL("INSERT INTO recipes (id, name, water, juice, price, defaultRemainingWeight, currentRemainingWeight, juiceChannel, imageUri, juiceType, waterSpeed, juiceSpeed) VALUES (3, '鸭屎香柠檬茶', 130, 150, 10, 1000, 1000, 3, NULL, '柠檬汁鸭屎香', 60, 60)")
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