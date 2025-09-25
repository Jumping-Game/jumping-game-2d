plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.bene.jump"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bene.jump"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "API_BASE", "\"http://10.0.2.2:8080\"")
        buildConfigField("String", "WS_URL", "\"ws://10.0.2.2:8081/v1/ws\"")
        buildConfigField("int", "PROTOCOL_PV", "1")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.7.2"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

kotlin {
    jvmToolchain(17)
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation("com.google.android.material:material:1.10.0")
    implementation(libs.bundles.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.androidx.datastore)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    kapt(libs.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.leakcanary.android)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.espresso)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.turbine)
}
