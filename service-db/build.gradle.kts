import org.liquibase.gradle.LiquibaseExtension

plugins {
    `java-library`
    alias(libs.plugins.liquibase.gradle)
}

dependencies {
    // `api` — consumers (service-app) get the Liquibase engine transitively.
    api(libs.liquibase.core)

    // Classpath for the Liquibase Gradle CLI tasks (update / status / rollback …).
    liquibaseRuntime(libs.liquibase.core)
    liquibaseRuntime(libs.picocli)
    liquibaseRuntime(libs.snakeyaml)
    liquibaseRuntime(libs.postgresql)
    liquibaseRuntime(sourceSets.main.get().output)
}

// All DB connection parameters live in `liquibase.properties` next to this
// build file. Override individual values on the CLI with -PliquibaseXxx=...
// (e.g. -PliquibaseUrl=... -PliquibasePassword=...).
configure<LiquibaseExtension> {
    activities.register("main") {
        this.arguments = mapOf(
            "defaultsFile" to "${project.projectDir}/liquibase.properties",
        )
    }
    runList = "main"
}
