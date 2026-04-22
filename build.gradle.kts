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
    apply(plugin = "checkstyle")

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

    plugins.withId("checkstyle") {
        extensions.configure<CheckstyleExtension> {
            toolVersion = ver("checkstyle")
            config = resources.text.fromFile(rootProject.file("checkstyle.xml"))
            configProperties["basedir"] = rootProject.projectDir.absolutePath
            isShowViolations = true
            maxWarnings = 0
        }

        tasks.withType<Checkstyle>().configureEach {
            include("**/*.java")
            classpath = files()
            reports {
                xml.required.set(false)
                html.required.set(true)
            }
        }

        tasks.named<Checkstyle>("checkstyleMain") {
            setSource("src/main/java")
        }
        tasks.named<Checkstyle>("checkstyleTest") {
            setSource("src/test/java")
        }

        tasks.named("check") {
            dependsOn(tasks.withType<Checkstyle>())
        }
    }
}
