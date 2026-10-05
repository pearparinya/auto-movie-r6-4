plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pearparinya.automovie"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pearparinya.automovie"
        minSdk = 26
        targetSdk = 35
        versionCode = 719
        versionName = "6.28"
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("src/main/AndroidManifest.xml")
            java.srcDirs("src/main/java")
            res.srcDirs("src/main/res")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// BUILD 719 feature transform: BUILD 718 + per-character identity lock + prop continuity.
val applyBuild719 by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("python3", "scripts/apply_build719.py")
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(applyBuild719)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
