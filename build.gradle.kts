// Project-level build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    // kotlin-compose plugin tidak terpisah di Kotlin 1.9.x
    // Compose compiler dikontrol lewat composeOptions.kotlinCompilerExtensionVersion
}
