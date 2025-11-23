package com.example.juicemachine.data.repository

import com.example.juicemachine.data.database.Recipe
import com.example.juicemachine.data.database.RecipeDao
import kotlinx.coroutines.flow.Flow

class RecipeRepository(private val recipeDao: RecipeDao) {

    val allRecipes: Flow<List<Recipe>> = recipeDao.getAllRecipes()

    suspend fun insertRecipe(recipe: Recipe): Long {
        return recipeDao.insertRecipe(recipe)
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

    suspend fun updateCurrentRemainingWeight(id: Int, weight: Int) {
        recipeDao.updateCurrentRemainingWeight(id, weight)
    }

    suspend fun resetCurrentRemainingToDefault(id: Int) {
        recipeDao.resetCurrentRemainingToDefault(id)
    }
    suspend fun updateRecipeFields(r: Recipe) {
        recipeDao.updateRecipeFields(
            id = r.id,
            name = r.name,
            water = r.water,
            juice = r.juice,
            price = r.price,
            defaultRemainingWeight = r.defaultRemainingWeight,
            currentRemainingWeight = r.currentRemainingWeight,
            juiceChannel = r.juiceChannel,
            imageUri = r.imageUri,
            juiceType = r.juiceType,
            hasPulp = r.hasPulp,
            pulpTotalCups = r.pulpTotalCups,
            pulpDecInterval = r.pulpDecInterval,
            pulpDecAmount = r.pulpDecAmount,
            waterSpeed = r.waterSpeed,
            juiceSpeed = r.juiceSpeed
        )
    }
    suspend fun updateJuiceSpeedDefaultIfZero(speed: Int = 60) {
        recipeDao.updateJuiceSpeedDefaultIfZero(speed)
    }
}