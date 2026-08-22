plugins {
    alias(libs.plugins.android.dynamic.feature)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// dynamic-featureモジュールはbaseモジュール（mobile）のapplicationId・署名設定・
// versionCode/versionNameを継承するため、applicationId/signingConfigs/versionは持たない
// （BL-036、mobileと別々のapplicationIdを持つ構成からの統合）。
android {
    namespace = "com.sesamiwear.wear"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation(project(":mobile"))
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.tiles.material)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.androidx.wear.watchface.complications.datasource)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    // SesameDeviceListReaderでデバイス一覧のJSONをデコードするために使用（BL-052）。
    implementation(libs.kotlinx.serialization.json)
    // mobile側がplay-services-wearable経由でguavaを実行時クラスパスへ提供するため、
    // compileOnlyでコンパイル時の参照のみとし、統合後のR8実行時の重複クラス検出
    // （com.google.common.util.concurrent.ListenableFutureがbase/featureの両方に
    // 含まれるエラー）を避ける（BL-036）。
    compileOnly(libs.guava)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.ui.tooling)
}
