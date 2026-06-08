plugins {
	java
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "roundtrip"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// --- Web ---
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-actuator")

	// --- Security + JWT ---
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

	// --- Persistence ---
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	runtimeOnly("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")

	// --- Cache / Async (Redisson) ---
	implementation("org.redisson:redisson-spring-boot-starter:4.3.1")

	// --- Push (Firebase FCM + APNs) ---
	implementation("com.google.firebase:firebase-admin:9.8.0")

	// --- AWS S3 ---
	implementation(platform("software.amazon.awssdk:bom:2.31.58"))
	implementation("software.amazon.awssdk:s3")

	// --- SVG Rasterization (Batik) ---
	implementation("org.apache.xmlgraphics:batik-transcoder:1.18")
	implementation("org.apache.xmlgraphics:batik-codec:1.18")

	// --- API Docs ---
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

	// --- Lombok ---
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	// --- Test ---
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
	archiveFileName.set("roundtrip.jar")
}

tasks.named<Test>("test") {
	useJUnitPlatform {
		excludeTags("external", "experiment")
	}
}

tasks.register<Test>("experimentTest") {
	description = "Runs AI pipeline experiments (latency / reproducibility) — calls real external APIs."
	group = "verification"
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform {
		includeTags("experiment")
	}
	outputs.upToDateWhen { false }
	testLogging {
		showStandardStreams = true
		events("passed", "failed", "skipped")
	}
}

tasks.register<Test>("externalTest") {
	description = "Runs tests tagged with @Tag(\"external\")"
	group = "verification"
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform {
		includeTags("external")
	}
}
