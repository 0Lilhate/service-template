plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":service-api"))

    // Intentionally NO dependency on :service-db.
    // Migrations are applied by the Liquibase Gradle plugin in service-db
    // as a pre-deploy step (CI) or manually by developers. The app never
    // touches DDL at startup — this is an enterprise pattern:
    //   * the app runs with a low-privilege DB user (no DDL grants)
    //   * rolling deploys don't race on schema changes
    //   * migrations are decoupled from app lifecycle
    //
    // To apply migrations:
    //   ./gradlew :service-db:update

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.springdoc.openapi)

    implementation(libs.micrometer.prometheus)

    // Kafka is always on the classpath but activated via feature toggle (app.kafka.enabled).
    implementation(libs.spring.kafka)

    runtimeOnly(libs.postgresql)
    // liquibase-core приходит транзитивно из service-db (api scope).

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.kafka.test)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.testcontainers.kafka)
}

springBoot {
    mainClass.set("com.example.service.ServiceApplication")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("${project.name}.jar")
}
