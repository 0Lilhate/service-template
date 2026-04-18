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
    // Keep only the supporting files we actually need. Without this filter
    // the generator emits a standalone `OpenApiGeneratorApplication` with
    // its own @SpringBootApplication + @ComponentScan, which causes the
    // real ServiceApplication to scan every controller twice (once with
    // camelCase bean name, once with FQCN via FullyQualifiedAnnotationBeanNameGenerator)
    // and fail with "Ambiguous mapping".
    // Any globalProperty overrides the default "generate everything" — so we
    // must explicitly re-enable apis/models alongside the supportingFiles filter.
    globalProperties.set(
        mapOf(
            "apis" to "",
            "models" to "",
            "supportingFiles" to "ApiUtil.java",
        )
    )
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
    val outputDirFile = generatedSources.get().asFile
    inputs.dir(openApiDir).withPropertyName("openApiSources").withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(generatedSources)
    doFirst {
        outputDirFile.deleteRecursively()
    }
}

tasks.named("compileJava") {
    dependsOn("openApiGenerate")
}
