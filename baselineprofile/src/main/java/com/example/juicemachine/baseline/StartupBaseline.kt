package com.example.juicemachine.baseline

import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBaseline {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = "com.example.juicemachine",
        metrics = listOf(StartupTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial()
    ) {
        // From home screen, start the default Activity
        pressHome()
        startActivityAndWait()
    }
}

