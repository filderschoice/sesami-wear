import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// リリース署名情報はlocal.properties（.gitignore対象、BL-032でユーザーが作成）から読み込む。
// 未設定でもassembleDebug等の通常ビルドには影響しない（release署名はunsignedのまま）。
val releaseKeystoreProperties =
    Properties().apply {
        val propertiesFile = rootProject.file("local.properties")
        if (propertiesFile.exists()) {
            FileInputStream(propertiesFile).use { load(it) }
        }
    }
val hasReleaseSigningConfig = releaseKeystoreProperties.containsKey("RELEASE_STORE_FILE")

android {
    namespace = "com.sesamiwear.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sesamiwear.mobile"
        minSdk = 26
        targetSdk = 36
        // リリースビルド時はscripts/release-build.bat経由で-PappVersionCode/-PappVersionNameを
        // 渡すことでバージョンを上書きできる（BL-035）。未指定時は既定値のまま。
        versionCode = (findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = findProperty("appVersionName") as String? ?: "0.9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = rootProject.file(releaseKeystoreProperties.getProperty("RELEASE_STORE_FILE"))
                storePassword = releaseKeystoreProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = releaseKeystoreProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = releaseKeystoreProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    // SesameDeviceListSyncerでデバイス一覧をJSON化するために使用（BL-052）。
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    debugImplementation(libs.androidx.ui.tooling)
}
