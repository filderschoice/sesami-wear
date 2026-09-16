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
        debug {
            // Play版（リリース署名）と同一端末へ併存インストールするため、デバッグ版はapplicationIdを
            // 分ける（BL-131）。署名が異なるPlay版へは上書き更新できず、入れ替えると保存済みの
            // 資格情報が消えるため。mobile/wearの双方へ同じサフィックスを付けることで、Data Layer APIが
            // 要求する「同一applicationId・同一署名」の条件をデバッグ版同士で満たす。
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"

            // 検証用にSesame APIの接続先をモックサーバーへ差し替えるための注入口（BL-132）。
            // -PsesameApiBaseUrl=http://<PCのIP>:8080/api/sesame2 のように渡したときだけ有効で、
            // 未指定なら空文字＝本番URLのまま。実資格情報・実デバイスを使わずに、施錠/解錠の成功を
            // 起点とする状態同期（ウィジェット⇔ウォッチのTile）を検証するために用いる
            // （rules/guardrails-unified.v1.md 12.5が許容するモック限定の疎通確認）。
            buildConfigField(
                "String",
                "SESAME_API_BASE_URL",
                "\"" + (findProperty("sesameApiBaseUrl") as String? ?: "") + "\"",
            )
        }

        release {
            // 本番は常に既定URL（BL-132）。debugと同名のフィールドが無いとmain配下から参照できない。
            buildConfigField("String", "SESAME_API_BASE_URL", "\"\"")
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
        buildConfig = true
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
    // 本アプリはfragmentを直接使わないが、推移的依存（mobileはplay-services-basement、wearは
    // watchface-complications-data→preference→appcompat）が古い1.1.0を解決し、Google Play Consoleで
    // 更新を求められたため、明示して引き上げる（BL-130）。
    implementation(libs.androidx.fragment)
    // ホーム画面ウィジェット（BL-121）。Glanceが推移的に持ち込む work-runtime 2.7.1（room 2.2.5 等を伴う）は
    // 古いため明示して引き上げる。2.11系は kotlin-stdlib をコンパイラ（2.0.21）より新しい2.1.20へ上げるため、
    // stdlib を変えない2.10系の最新を使う（BL-130 と同じ判断基準）。
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime)
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
