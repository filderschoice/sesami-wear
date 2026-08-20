# kotlinx.serializationでシリアライズ対象となるクラス（@Serializable付き）を保護する。
# coreモジュール経由でSesameStatus等のシリアライズ対象クラスがwearのAPKにも含まれるため、
# mobileと同じルールを適用する（詳細はmobile/proguard-rules.proのコメント参照）。
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
