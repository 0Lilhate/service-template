import org.liquibase.gradle.LiquibaseExtension

plugins {
    `java-library`
    alias(libs.plugins.liquibase.gradle)
}

dependencies {
    api(libs.liquibase.core)

    liquibaseRuntime(libs.liquibase.core)
    liquibaseRuntime(libs.picocli)
    liquibaseRuntime(libs.snakeyaml)
    liquibaseRuntime(libs.postgresql)
    liquibaseRuntime(sourceSets.main.get().output)
}

configure<LiquibaseExtension> {
    activities.register("main") {
        this.arguments = mapOf(
            "defaultsFile" to "${project.projectDir}/liquibase.properties",
        )
    }
    runList = "main"
}
