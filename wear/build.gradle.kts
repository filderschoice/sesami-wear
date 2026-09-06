import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// リリース署名情報はlocal.properties（.gitignore対象、BL-032でユーザーが作成）から読み込む。
// mobileと同一のKeystoreを使う（同一applicationIdの成果物は同じ鍵で署名する必要がある）。
val releaseKeystoreProperties =
    Properties().apply {
        val propertiesFile = rootProject.file("local.properties")
        if (propertiesFile.exists()) {
            FileInputStream(propertiesFile).use { load(it) }
        }
    }
val hasReleaseSigningConfig = releaseKeystoreProperties.containsKey("RELEASE_STORE_FILE")

// wearは独立したapplicationモジュールで、mobileとは別のAABとしてビルドする（BL-090）。
// Googleは単一App BundleへWear OSをdynamic featureとして同梱する構成を非サポートとしており、
// Play ConsoleはWear OS向けリリースを専用トラックで公開することを必須としている。
android {
    namespace = "com.sesamiwear.wear"
    compileSdk = 36

    defaultConfig {
        // スマホ用アプリと同一のapplicationIdを使う。Googleは、同一パッケージ名で同じストア掲載
        // ページへ登録することを推奨している（別々のapplicationIdだと別アプリ扱いになる）。
        applicationId = "com.sesamiwear.mobile"
        minSdk = 26
        targetSdk = 36
        // versionCodeは全フォームファクタで一意である必要があるため、mobileとは独立した系列
        // （1001始まり）を使う（BL-093）。リリースビルド時はscripts/release-build.bat経由で
        // -PappWearVersionCode/-PappWearVersionNameを渡して上書きする。
        versionCode = (findProperty("appWearVersionCode") as String?)?.toIntOrNull() ?: 1001
        versionName = findProperty("appWearVersionName") as String? ?: "0.9.0"

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
    // Futures/SettableFuture等の実装クラスを実行時に必要とする。単一AAB構成では
    // base（mobile）がランタイムクラスパスへ提供していたためcompileOnlyで足りていたが、
    // 独立成果物となった以降は自前で解決する必要がある（BL-092）。
    implementation(libs.guava)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.ui.tooling)
}
