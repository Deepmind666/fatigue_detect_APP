package com.example.juicemachine

import android.app.Application
import com.example.juicemachine.data.repository.RecipeRepository
import com.example.juicemachine.data.database.AppDatabase
import com.example.juicemachine.data.database.CupConfig
import com.example.juicemachine.data.database.Recipe
import com.example.juicemachine.data.hardware.HardwareManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.example.juicemachine.data.database.RecipeDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.util.DebugLogger

class JuiceMachineApplication : Application(), ImageLoaderFactory {

    val applicationScope = CoroutineScope(SupervisorJob())

    // Database and repository are now initialized lazily and asynchronously
    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()

    lateinit var database: AppDatabase
    var repository: RecipeRepository? = null // Repository is nullable now
    lateinit var hardwareManager: HardwareManager

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .logger(DebugLogger())
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        // Launch the initialization in the background
        applicationScope.launch(Dispatchers.IO) {
            database = AppDatabase.getDatabase(this@JuiceMachineApplication)
            hardwareManager = HardwareManager.getInstance(this@JuiceMachineApplication, this)
            hardwareManager.connect() // Automatically connect on startup
            val recipeDao = database.recipeDao()
            if (recipeDao.getRecipeCount() == 0) {
                AppDatabase.populateDatabase(recipeDao)
            }
            repository = RecipeRepository(recipeDao, hardwareManager)
            _isReady.value = true // Signal that the app is ready
        }
    }
} 