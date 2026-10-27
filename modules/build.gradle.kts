plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "moe.rukamori.archivetune.modules"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.quickjs.kt)
    implementation(libs.jsoup)
}
