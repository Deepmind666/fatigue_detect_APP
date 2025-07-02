package com.example.juicemachine.data.repository

import com.example.juicemachine.data.database.Recipe
import com.example.juicemachine.data.database.RecipeDao
import com.example.juicemachine.data.hardware.HardwareManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecipeRepository(
    private val recipeDao: RecipeDao,
    private val hardwareManager: HardwareManager
) {

    val allRecipes: Flow<List<Recipe>> = recipeDao.getAllRecipes()
    val isMachineConnected: Flow<Boolean> = hardwareManager.isConnected

    suspend fun insertRecipe(recipe: Recipe) {
        recipeDao.insertRecipe(recipe)
    }

    suspend fun updateRecipe(recipe: Recipe) {
        recipeDao.updateRecipe(recipe)
    }

    suspend fun deleteRecipe(recipe: Recipe) {
        recipeDao.deleteRecipe(recipe)
    }

    suspend fun getRecipeById(id: Long): Recipe? {
        return recipeDao.getRecipeById(id)
    }
} 