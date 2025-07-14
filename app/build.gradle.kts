plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

}

android {

    namespace = "com.kulipai.luahook"
    compileSdk = libs.versions.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true    // 开启BuildConfig类的生成
        aidl = true           // 启用aidl
    }

    defaultConfig {
        applicationId = "com.kulipai.luahook"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = 36
        versionCode = 29
        versionName = "3.9.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true // 启用代码压缩
            isShrinkResources = true // 启用资源压缩
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

//        isCoreLibraryDesugaringEnabled = true

    }
    kotlinOptions {
        jvmTarget = "17"
    }

    aaptOptions {
        additionalParameters += listOf("--package-id", "0x69", "--allow-reserved-package-id")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(libs.okhttp)
    compileOnly(fileTree("compileOnly"))
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment.ktx)
    // The core module that provides APIs to a shell
    implementation(libs.core)
    implementation(libs.kotlin.reflect)
    implementation(libs.xphelper)
    implementation(libs.dexkit)
    implementation(libs.ripple.components.android)
    implementation(libs.ripple.insets)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.shizuku.api)
    implementation(libs.provider) // 如果你需要使用 ShizukuProvider

    //Xposed service 100
    compileOnly(project(":libxposed:api"))
    implementation(project(":libxposed:service"))


}
