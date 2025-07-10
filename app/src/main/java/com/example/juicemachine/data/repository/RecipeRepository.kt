package com.example.juicemachine.data.repository

import com.example.juicemachine.data.database.Recipe
import com.example.juicemachine.data.database.RecipeDao
import kotlinx.coroutines.flow.Flow

class RecipeRepository(private val recipeDao: RecipeDao) {

    val allRecipes: Flow<List<Recipe>> = recipeDao.getAllRecipes()

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

    suspend fun updateRemainWeight(id: Int, remainWeight: Int) {
        recipeDao.updateRemainWeight(id, remainWeight)
    }
} 