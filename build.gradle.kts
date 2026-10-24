plugins {
    id("com.android.library") version "8.5.2"
    kotlin("android") version "2.0.20"
    `maven-publish`
}

group = "com.github.Paywallex"
version = "1.0.0"

android {
    namespace = "app.paywallex.sdk"
    compileSdk = 35
    defaultConfig { minSdk = 23 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    publishing { singleVariant("release") { withSourcesJar() } }
}

dependencies {
    implementation("androidx.browser:browser:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}

// Publication JitPack : implementation("com.github.Paywallex:paywallex-android:1.0.0"). Signé : Superagent.
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.Paywallex"; artifactId = "paywallex-android"; version = "1.0.0"
            }
        }
    }
}
