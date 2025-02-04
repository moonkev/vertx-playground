import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.jvm.JvmTargetValidationMode.IGNORE
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import com.google.protobuf.gradle.*

plugins {
  kotlin("jvm") version "2.0.0"
  application
  id("com.gradleup.shadow") version "8.3.5"
  id("com.google.protobuf") version "0.9.4"
}

group = "com.github.moonkev"
version = "1.0.0-SNAPSHOT"

repositories {
  mavenCentral()
}

sourceSets {
  main {
    proto {
      srcDir("src/main/proto")
    }
  }
  test {
    runtimeClasspath += sourceSets.test.get().resources.sourceDirectories
  }
}

tasks.distTar.configure {
  enabled = false
}

tasks.distZip.configure {
  enabled = false
}

val vertxVersion = "5.0.0.CR4"
val junitJupiterVersion = "5.9.1"
val launcherClassName = "io.vertx.launcher.application.VertxApplication"

application {
  mainClass.set(launcherClassName)
}

dependencies {
  implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
  implementation("com.google.protobuf:protobuf-java:4.29.3")
  implementation("com.graphql-java:graphql-java:22.3")
  implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")
  implementation("org.slf4j:slf4j-api:2.0.16")
  implementation("ch.qos.logback:logback-classic:1.5.16")
  implementation("io.grpc:grpc-protobuf:1.69.1")
  implementation("io.grpc:grpc-stub:1.69.1")
  implementation("io.vertx:vertx-config")
  implementation("io.vertx:vertx-config-hocon")
  implementation("io.vertx:vertx-grpc-client")
  implementation("io.vertx:vertx-grpc-server")
  implementation("io.vertx:vertx-lang-kotlin")
  implementation("io.vertx:vertx-lang-kotlin-coroutines")
  implementation("io.vertx:vertx-launcher-application")
  implementation("io.vertx:vertx-reactive-streams")
  implementation("io.vertx:vertx-rx-java3")
  implementation("io.vertx:vertx-shell")
  implementation("io.vertx:vertx-web-graphql")
  implementation("io.vertx:vertx-zookeeper")
  implementation("org.apache.zookeeper:zookeeper:3.9.3")
  if (JavaVersion.current().isJava9Compatible) {
    // Workaround for @javax.annotation.Generated
    // see: https://github.com/grpc/grpc-java/issues/3633
    implementation("javax.annotation:javax.annotation-api:1.3.1")
  }

  testImplementation("io.vertx:vertx-junit5")
  testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
}

val compileKotlin: KotlinCompile by tasks

kotlin.compilerOptions.jvmTarget.set(JvmTarget.JVM_21)

tasks {
  withType<KotlinJvmCompile>().configureEach {
    jvmTargetValidationMode.set(IGNORE)
  }

  withType<ShadowJar> {
    archiveClassifier = "uber"
    archiveVersion = ""
    mergeServiceFiles()
  }

  withType<Test> {
    useJUnitPlatform()
    testLogging {
      events = setOf(PASSED, SKIPPED, FAILED)
    }
  }

  withType<Copy> {
    filesMatching("**/*.proto") {
      duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
  }
}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:4.29.3"
  }
  plugins {
    id("grpc") {
      artifact = "io.grpc:protoc-gen-grpc-java:1.69.1"
    }
    id("vertx") {
      artifact = "io.vertx:vertx-grpc-protoc-plugin2:5.0.0.CR3"
    }
  }
  generateProtoTasks {
    ofSourceSet("main").forEach {
      it.plugins {
        id("grpc") { }
        id("vertx") { }
      }
    }
  }
}
