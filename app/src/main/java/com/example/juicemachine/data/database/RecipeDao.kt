package com.example.juicemachine.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes ORDER BY id ASC")
    fun getAllRecipes(): Flow<List<Recipe>>

    @Query("DELETE FROM recipes")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun getRecipeCount(): Int

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getRecipeById(id: Long): Recipe?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recipes: List<Recipe>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: Recipe)

    @Update
    suspend fun updateRecipe(recipe: Recipe)

    @Delete
    suspend fun deleteRecipe(recipe: Recipe)

    // 更新当前剩余重量
    @Query("UPDATE recipes SET currentRemainingWeight = :weight WHERE id = :id")
    suspend fun updateCurrentRemainingWeight(id: Int, weight: Int)

    // 将当前剩余重量重置为默认值
    @Query("UPDATE recipes SET currentRemainingWeight = defaultRemainingWeight WHERE id = :id")
    suspend fun resetCurrentRemainingToDefault(id: Int)
}