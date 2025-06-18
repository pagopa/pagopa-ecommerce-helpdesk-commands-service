import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.9.22"
  kotlin("plugin.spring") version "1.9.24"
  id("java")
  id("org.springframework.boot") version "3.3.2"
  id("io.spring.dependency-management") version "1.1.6"
  id("org.openapi.generator") version "6.3.0"
  id("org.graalvm.buildtools.native") version "0.10.2"
  id("com.diffplug.spotless") version "6.18.0"
  id("com.dipien.semantic-version") version "2.0.0" apply false
  id("org.sonarqube") version "4.2.0.3129"
  jacoco
}

group = "it.pagopa.helpdeskcommands"

version = "0.19.0"

description = "pagopa-helpdeskcommands-service"

sourceSets {
  main { java { srcDirs("${layout.buildDirectory.get().asFile.path}/generated/src/main/java") } }
}

springBoot {
  mainClass.set("it.pagopa.helpdeskcommands.HelpDeskCommandsApplicationKt")
  buildInfo { properties { additional.set(mapOf("description" to project.description)) } }
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

repositories {
  mavenCentral()
  mavenLocal()
}

val mockWebServerVersion = "4.12.0"
val ecsLoggingVersion = "1.5.0"

object Deps {
  const val azureSpringCloudDepsVersion = "5.22.0"
  const val mongoReactiveVersion = "3.5.0"
  const val ecommerceCommonsVersion = "1.37.2"
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("org.openapitools:jackson-databind-nullable:0.2.6")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("io.arrow-kt:arrow-core:1.2.4")
  implementation("io.swagger.core.v3:swagger-annotations:2.2.8")
  implementation("it.pagopa:pagopa-ecommerce-commons:${Deps.ecommerceCommonsVersion}")

  // ECS logback encoder
  implementation("co.elastic.logging:logback-ecs-encoder:$ecsLoggingVersion")

  // mongodb
  implementation(
    "org.springframework.boot:spring-boot-starter-data-mongodb-reactive:${Deps.mongoReactiveVersion}"
  )

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.projectreactor:reactor-test")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testImplementation("org.jetbrains.kotlin:kotlin-test")
  testImplementation("com.squareup.okhttp3:mockwebserver:$mockWebServerVersion")
  testImplementation("com.squareup.okhttp3:okhttp:$mockWebServerVersion")
  testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin { compilerOptions { freeCompilerArgs.addAll("-Xjsr305=strict") } }

kotlin { jvmToolchain(21) }

// Dependency locking - lock all dependencies
// dependencyLocking { lockAllConfigurations() }

tasks.create("applySemanticVersionPlugin") {
  dependsOn("prepareKotlinBuildScriptModel")
  apply(plugin = "com.dipien.semantic-version")
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
  kotlin {
    toggleOffOn()
    targetExclude("build/**/*")
    ktfmt().kotlinlangStyle()
  }
  kotlinGradle {
    toggleOffOn()
    targetExclude("build/**/*.kts")
    ktfmt().googleStyle()
  }
  java {
    target("**/*.java")
    targetExclude("build/**/*")
    eclipse().configFile("eclipse-style.xml")
    toggleOffOn()
    removeUnusedImports()
    trimTrailingWhitespace()
    endWithNewline()
  }
}

tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("helpdeskcommands-v1") {
  generatorName.set("spring")
  inputSpec.set("$rootDir/api-spec/v1/openapi.yaml")
  outputDir.set(layout.buildDirectory.get().dir("generated").asFile.toString())
  apiPackage.set("it.pagopa.generated.helpdeskcommands.api")
  modelPackage.set("it.pagopa.generated.helpdeskcommands.model")
  generateApiTests.set(false)
  generateApiDocumentation.set(false)
  generateApiTests.set(false)
  generateModelTests.set(false)
  library.set("spring-boot")
  modelNameSuffix.set("Dto")
  configOptions.set(
    mapOf(
      "swaggerAnnotations" to "false",
      "openApiNullable" to "true",
      "interfaceOnly" to "true",
      "hideGenerationTimestamp" to "true",
      "skipDefaultInterface" to "true",
      "useSwaggerUI" to "false",
      "reactive" to "true",
      "useSpringBoot3" to "true",
      "useJakartaEe" to "true",
      "oas3" to "true",
      "generateSupportingFiles" to "true",
      "enumPropertyNaming" to "UPPERCASE"
    )
  )
}

tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("redirect-api-v1") {
  generatorName.set("spring")
  inputSpec.set("$rootDir/api-spec/client/openapi/redirect/redirect-api.yaml")
  outputDir.set(layout.buildDirectory.get().dir("generated").asFile.toString())
  apiPackage.set("it.pagopa.generated.ecommerce.redirect.v1.api")
  modelPackage.set("it.pagopa.generated.ecommerce.redirect.v1.dto")
  generateApiTests.set(false)
  generateApiDocumentation.set(false)
  generateApiTests.set(false)
  generateModelTests.set(false)
  library.set("spring-boot")
  modelNameSuffix.set("Dto")
  configOptions.set(
    mapOf(
      "swaggerAnnotations" to "false",
      "openApiNullable" to "true",
      "interfaceOnly" to "true",
      "hideGenerationTimestamp" to "true",
      "skipDefaultInterface" to "true",
      "useSwaggerUI" to "false",
      "reactive" to "true",
      "useSpringBoot3" to "true",
      "useJakartaEe" to "true",
      "oas3" to "true",
      "generateSupportingFiles" to "true",
      "enumPropertyNaming" to "UPPERCASE"
    )
  )
}

tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("npg-api") {
  generatorName.set("java")
  inputSpec.set("$rootDir/npg-api/npg-api.yaml")
  outputDir.set(layout.buildDirectory.get().dir("generated").asFile.toString())
  apiPackage.set("it.pagopa.generated.npg.api")
  modelPackage.set("it.pagopa.generated.npg.model")
  generateApiTests.set(false)
  generateApiDocumentation.set(false)
  generateApiTests.set(false)
  generateModelTests.set(false)
  library.set("webclient")
  modelNameSuffix.set("Dto")
  configOptions.set(
    mapOf(
      "swaggerAnnotations" to "false",
      "openApiNullable" to "true",
      "interfaceOnly" to "true",
      "hideGenerationTimestamp" to "true",
      "skipDefaultInterface" to "true",
      "useSwaggerUI" to "false",
      "reactive" to "true",
      "useSpringBoot3" to "true",
      "useJakartaEe" to "true",
      "oas3" to "true",
      "generateSupportingFiles" to "false"
    )
  )
}

tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>(
  "node-forwarder-api-v1"
) {
  generatorName.set("java")
  remoteInputSpec.set(
    "https://raw.githubusercontent.com/pagopa/pagopa-infra/main/src/core/api/node_forwarder_api/v1/_openapi.json.tpl"
  )
  outputDir.set(layout.buildDirectory.get().dir("generated").asFile.toString())
  library.set("webclient")
  generateApiDocumentation.set(false)
  generateApiTests.set(false)
  generateModelTests.set(false)
  configOptions.set(
    mapOf(
      "swaggerAnnotations" to "false",
      "openApiNullable" to "true",
      "interfaceOnly" to "true",
      "hideGenerationTimestamp" to "true",
      "skipDefaultInterface" to "true",
      "useSwaggerUI" to "false",
      "reactive" to "true",
      "useSpringBoot3" to "true",
      "useJakartaEe" to "true",
      "oas3" to "true",
      "generateSupportingFiles" to "false"
    )
  )
  modelPackage.set("it.pagopa.generated.nodeforwarder.v1.dto")
  apiPackage.set("it.pagopa.generated.nodeforwarder.v1.dto")
  modelNameSuffix.set("Dto")
}

tasks.register<Exec>("installLibs") {
  description = "Installs the commons library for this project."
  group = "commons"
  val buildCommons = providers.gradleProperty("buildCommons")
  onlyIf("To build commons library run gradle build -PbuildCommons") { buildCommons.isPresent }
  commandLine("sh", "./pagopa-ecommerce-commons-maven-install.sh", Deps.ecommerceCommonsVersion)
}

tasks.withType<KotlinCompile> {
  dependsOn(
    "helpdeskcommands-v1",
    "npg-api",
    "node-forwarder-api-v1",
    "redirect-api-v1",
    "installLibs"
  )
  // kotlinOptions.jvmTarget = "21"
}

tasks.register("printCommonsVersion") {
  description = "Prints the referenced commons library version."
  group = "commons"
  doLast { print(Deps.ecommerceCommonsVersion) }
}

tasks.test {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
}

tasks.jacocoTestReport {
  dependsOn(tasks.test) // tests are required to run before generating the report
  classDirectories.setFrom(
    files(
      classDirectories.files.map {
        fileTree(it).matching {
          exclude("it/pagopa/helpdeskcommands/HelpDeskCommandsApplicationKt.class")
        }
      }
    )
  )
  reports { xml.required.set(true) }
}

tasks.processResources { filesMatching("application.properties") { expand(project.properties) } }

graalvmNative {
  toolchainDetection = true

  binaries {
    named("main") {
      javaLauncher =
        javaToolchains.launcherFor {
          languageVersion = JavaLanguageVersion.of(21)
          vendor.set(JvmVendorSpec.GRAAL_VM)
        }
      /*
      Add --strict-image-heap to prevent class initialization issues during native image building.
      This flag ensures problematic classes (like XML processors) are properly initialized at runtime
      rather than build time. Required for GraalVM 21, became default in GraalVM 22+.
      */
      buildArgs.add("--strict-image-heap")
      buildArgs.add("-H:+AddAllCharsets")
    }
  }

  metadataRepository {
    enabled.set(true)
    version.set("0.3.8")
  }
}
