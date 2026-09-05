# kotlinx.serializationでシリアライズ対象となるクラス（@Serializable付き）を保護する。
# コンパイル時生成される$$serializerクラスとcompanion.serializer()呼び出しを縮小・難読化から守らないと、
# リリースビルドでJSONのシリアライズ/デシリアライズが実行時に失敗する。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# リリースビルドでは切り分け用のデバッグログ（Log.d / Log.v）の呼び出しを除去する（BL-083）。
# 出力内容はパス・成否・状態の真偽値のみで資格情報は含めていないが、配布物へ内部状態を残さないため。
# 障害調査に必要な Log.w / Log.e は残す。isMinifyEnabled=true かつ
# proguard-android-optimize.txt（最適化有効）を使うreleaseビルドでのみ適用される。
# wear（dynamic feature）は独自のproguardFilesを持たず、base（mobile）の本設定が適用される。
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

-keep,includedescriptorclasses class com.sesamiwear.**$$serializer { *; }
-keepclassmembers class com.sesamiwear.** {
    *** Companion;
}
-keepclasseswithmembers class com.sesamiwear.** {
    kotlinx.serialization.KSerializer serializer(...);
}
