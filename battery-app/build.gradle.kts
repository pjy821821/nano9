// 루트에는 어디서든 해석되는 플러그인만 둔다. Android 플러그인은 :app 모듈이 직접 선언한다
// (SDK가 없는 환경에서 :app을 빼고 :core만 빌드할 수 있게).
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}
