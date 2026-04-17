import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.openapi.generator) apply false
    alias(libs.plugins.liquibase.gradle) apply false
}

allprojects {
    group = rootProject.group
    version = rootProject.version
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    // `libs` type-safe accessor is not visible inside subprojects { },
    // so read the catalog via the VersionCatalogsExtension.
    val catalog = rootProject.extensions
        .getByType(VersionCatalogsExtension::class.java)
        .named("libs")

    fun ver(alias: String): String =
        catalog.findVersion(alias).orElseThrow { error("Missing version: $alias") }.requiredVersion

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(ver("java").toInt()))
        }
    }

    extensions.configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:${ver("springBoot")}")
            mavenBom("org.testcontainers:testcontainers-bom:${ver("testcontainers")}")
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
        }
    }
}
