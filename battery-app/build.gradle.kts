// 모든 플러그인을 루트에서 한 번만(apply false) 해석한다. Gradle은 한 플러그인을 두 곳에서
// 서로 다른 방식으로 요청하면 "already on the classpath with an unknown version"으로 실패하므로,
// 루트와 모듈이 같은 버전 카탈로그 항목을 쓰도록 여기서 모두 선언하고 모듈은 alias만 적용한다.
// Android 플러그인은 해석(다운로드)만 되고 적용은 :app에서만 하므로, SDK 없는 환경에서도 :core는 빌드된다.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
}
