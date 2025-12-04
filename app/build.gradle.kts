plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.linuxsimulator"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.linuxsimulator"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.room.common.jvm)
    implementation(libs.test.core)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.okhttp)
    androidTestImplementation (libs.junit.v115)
    androidTestImplementation (libs.espresso.core.v351)
    androidTestImplementation (libs.test.core)
    androidTestImplementation (libs.rules)
    androidTestImplementation (libs.runner)

    androidTestImplementation (libs.espresso.web)


    implementation (libs.room.runtime)
    annotationProcessor (libs.room.compiler)
    implementation(libs.core)
    implementation(libs.activity.v160)
    implementation (libs.lifecycle.viewmodel)
    implementation (libs.lifecycle.livedata)



    implementation(libs.org.eclipse.jgit)
    implementation(libs.jsch)
}