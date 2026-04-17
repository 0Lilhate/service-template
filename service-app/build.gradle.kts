plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":service-api"))
    runtimeOnly(project(":service-db"))

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
