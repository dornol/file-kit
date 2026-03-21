plugins {
    alias(libs.plugins.vanniktech.publish) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

allprojects {

    group = "io.github.dornol"
    version = "0.1.5"

    repositories {
        mavenCentral()
    }

    if (tasks.names.none { it == "prepareKotlinBuildScriptModel" }) {
        tasks.register("prepareKotlinBuildScriptModel") {}
    }
}
