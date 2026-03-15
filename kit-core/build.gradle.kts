plugins {
    id("java")
    id("jacoco")
    alias(libs.plugins.vanniktech.publish)
    id("signing")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.slf4j.api)
    compileOnly(libs.jakarta.validation.api)
    compileOnly(libs.jspecify)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.slf4j.simple)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        csv.required = true
    }
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}

mavenPublishing {
    signAllPublications()
    publishToMavenCentral()

    coordinates("io.github.dornol", "file-kit-core", project.version.toString())

    pom {
        name = "file-kit-core"
        description = "Core file validation, upload and download kit - pure Java, no framework dependency"
        url = "https://github.com/dornol/file-kit/"

        licenses {
            license {
                name = "MIT"
                url = "https://github.com/dornol/file-kit/blob/main/LICENSE"
            }
        }

        developers {
            developer {
                id = "dornol"
                name = "dhkim"
                email = "dhkim@dornol.dev"
                url = "https://github.com/dornol/"
            }
        }

        scm {
            url = "https://github.com/dornol/file-kit/"
            connection = "scm:git:git://github.com/dornol/file-kit.git"
            developerConnection = "scm:git:ssh://git@github.com/dornol/file-kit.git"
        }
    }
}
