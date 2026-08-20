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

-keep,includedescriptorclasses class com.sesamiwear.**$$serializer { *; }
-keepclassmembers class com.sesamiwear.** {
    *** Companion;
}
-keepclasseswithmembers class com.sesamiwear.** {
    kotlinx.serialization.KSerializer serializer(...);
}
