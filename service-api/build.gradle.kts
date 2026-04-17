plugins {
    alias(libs.plugins.openapi.generator)
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.springdoc.openapi)
    implementation(libs.jackson.databind.nullable)
    implementation(libs.swagger.annotations)
    implementation(libs.jakarta.validation.api)
    implementation(libs.jakarta.annotation.api)
    compileOnly(libs.jakarta.servlet.api)
}

val openApiDir = layout.projectDirectory.dir("openapi")
val openApiSpec = openApiDir.file("service-api.yaml").asFile.absolutePath
val generatedSources = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set(openApiSpec)
    outputDir.set(generatedSources.get().asFile.absolutePath)
    apiPackage.set("com.example.service.api.controller")
    modelPackage.set("com.example.service.api.model")
    invokerPackage.set("com.example.service.api")
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    configOptions.set(
        mapOf(
            "useSpringBoot3" to "true",
            "delegatePattern" to "true",
            "documentationProvider" to "springdoc",
            "useJakartaEe" to "true",
            "openApiNullable" to "false",
            "useTags" to "true",
            "hideGenerationTimestamp" to "true",
            "serializableModel" to "true",
            "interfaceOnly" to "false",
        )
    )
}

sourceSets {
    main {
        java.srcDir(generatedSources.map { it.dir("src/main/java") })
    }
}

// Any change under openapi/** (paths/, components/, root spec) must invalidate
// the openApiGenerate cache — by default the task only tracks `inputSpec`
// and wouldn't rebuild when a $ref'd file changes.
tasks.named("openApiGenerate") {
    inputs.dir(openApiDir).withPropertyName("openApiSources").withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.named("compileJava") {
    dependsOn("openApiGenerate")
}
