package com.example.juicemachine.data.database

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.juicemachine.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Recipe::class], version = 6, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        suspend fun getDatabase(context: Context): AppDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "recipe_database"
            )
            .fallbackToDestructiveMigration()
            .build()
            INSTANCE = instance
            return instance
        }

        suspend fun populateDatabase(recipeDao: RecipeDao) {
            recipeDao.insertRecipe(Recipe(name = "桃你欢心", imageResId = R.drawable.tao_ni_huan_xin))
            recipeDao.insertRecipe(Recipe(name = "牛油果生椰拿铁", imageResId = R.drawable.niu_you_guo))
            recipeDao.insertRecipe(Recipe(name = "鸭屎香柠檬茶", imageResId = R.drawable.ya_shi_xiang))
            recipeDao.insertRecipe(Recipe(name = "缤纷百果", imageResId = R.drawable.bin_fen_bai_guo))
        }
    }
}