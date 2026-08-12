plugins {
    id("java-library")
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
    api(project(":kit-core"))

    compileOnly(libs.slf4j.api)
    compileOnly(libs.jakarta.validation.api)
    compileOnly(libs.jspecify)
    compileOnly(libs.spring.web)
    compileOnly(libs.spring.webflux)
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.tika.core)
    compileOnly(libs.micrometer.core)
    compileOnly("org.springframework.boot:spring-boot-actuator:${libs.versions.spring.boot.get()}")
    compileOnly("org.springframework.boot:spring-boot-health:${libs.versions.spring.boot.get()}")

    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.slf4j.simple)
    testImplementation(libs.jakarta.validation.api)
    testImplementation(libs.hibernate.validator)
    testImplementation(libs.expressly)
    testImplementation(libs.mockito.core)
    testImplementation(libs.spring.web)
    testImplementation(libs.spring.webflux)
    testImplementation(libs.spring.boot.autoconfigure)
    testImplementation("org.springframework.boot:spring-boot-actuator:${libs.versions.spring.boot.get()}")
    testImplementation("org.springframework.boot:spring-boot-health:${libs.versions.spring.boot.get()}")
    testImplementation(libs.spring.boot.test)
    testImplementation(libs.assertj.core)
    testImplementation(libs.tika.core)
    testImplementation(libs.pdfbox)
    testImplementation(libs.micrometer.core)
    testImplementation(libs.micrometer.test)
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

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}

mavenPublishing {
    signAllPublications()
    publishToMavenCentral()

    coordinates("io.github.dornol", "file-kit-spring-boot-starter", project.version.toString())

    pom {
        name = "file-kit-spring-boot-starter"
        description = "Spring Boot starter for file-kit - auto-configured MultipartFile validation"
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
