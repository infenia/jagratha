plugins {
    base
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.cyclonedx) apply false
}

allprojects {
    group = "com.infenia.yukta"
    version = "0.0.1-SNAPSHOT"

    dependencyLocking {
        lockAllConfigurations()
    }
}
