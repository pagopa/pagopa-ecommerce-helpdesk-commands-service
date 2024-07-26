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
  jacoco
}

group = "it.pagopa.helpdeskcommands"

version = "0.0.1"

description = "pagopa-helpdeskcommands-service"

sourceSets { main { java { srcDirs("$buildDir/generated/src/main/java") } } }

springBoot {
  mainClass.set("it.pagopa.helpdeskcommands.HelpDeskCommandsApplicationKt")
  buildInfo { properties { additional.set(mapOf("description" to project.description)) } }
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

repositories { mavenCentral() }

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("org.openapitools:jackson-databind-nullable:0.2.6")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("io.swagger.core.v3:swagger-annotations:2.2.8")
  // implementation("jakarta.xml.bind:jakarta.xml.bind-api")

  // mongodb
  // implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.projectreactor:reactor-test")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testImplementation("org.jetbrains.kotlin:kotlin-test")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin { compilerOptions { freeCompilerArgs.addAll("-Xjsr305=strict") } }

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(21) }

// Dependency locking - lock all dependencies
// dependencyLocking { lockAllConfigurations() }

/*tasks.create("applySemanticVersionPlugin") {
    dependsOn("prepareKotlinBuildScriptModel")
    apply(plugin = "com.dipien.semantic-version")
}*/

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
  outputDir.set("$buildDir/generated")
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
      "oas3" to "true",
      "generateSupportingFiles" to "true",
      "enumPropertyNaming" to "UPPERCASE"
    )
  )
}

tasks.withType<KotlinCompile> {
  dependsOn("helpdeskcommands-v1")
  // kotlinOptions.jvmTarget = "21"
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
    }
  }

  metadataRepository {
    enabled.set(true)
    version.set("0.3.8")
  }
}
