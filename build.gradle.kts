import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotbugs)
    checkstyle
}

group = "com.rightcrowd"
version = "0.1.0"
description = "sopstore — enterprise SOP management platform"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.springframework.modulith") {
            useVersion(libs.versions.springModulith.get())
        }
    }
}

dependencies {
    // Spring Boot 4
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.client)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.spring.boot.starter.batch)
    implementation(libs.spring.boot.starter.integration)
    implementation(libs.spring.boot.starter.opentelemetry)
    implementation(libs.spring.security.saml2.service.provider)

    // Spring Modulith
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.spring.modulith.starter.jpa)
    implementation(libs.spring.modulith.events.api)
    implementation(libs.spring.modulith.events.jpa)
    implementation(libs.spring.modulith.actuator)
    implementation(libs.spring.modulith.observability)

    // Persistence
    implementation(libs.spring.boot.flyway) // Boot 4 autoconfig module that actually runs Flyway
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgres.driver)

    // API / docs
    implementation(libs.springdoc.openapi.webmvc)

    // Domain libs
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.minio)
    implementation(libs.totp)
    implementation(libs.commons.text)
    implementation(libs.diff.utils)
    implementation(libs.openhtmltopdf.pdfbox) // styled PDF export of procedures (HTML/CSS → PDF)
    implementation(libs.webauthn.server.core)

    // Nullness
    implementation(libs.jspecify)

    // ErrorProne + NullAway
    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    // Test
    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.mockito", module = "mockito-core")
    }
    testImplementation(libs.spring.security.test)
    testImplementation(libs.spring.modulith.test)
    testImplementation(libs.spring.modulith.docs)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.testcontainers.redis)
    testImplementation(libs.testcontainers.minio)
    testImplementation(libs.testcontainers.keycloak)
    testImplementation(libs.archunit.junit5)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--enable-preview", "-Xlint:all", "-Werror"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        option("NullAway:AnnotatedPackages", "com.rightcrowd.sopstore")
        option("NullAway:JSpecifyMode", "true")
        error("NullAway")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-preview")
    systemProperty("spring.profiles.active", "test")
}

tasks.withType<JavaExec>().configureEach { jvmArgs("--enable-preview") }

// Local dev only: `bootRun` is never used in production (the container runs the built jar).
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // Silence the OTLP metrics exporter that logs "Connection refused" every minute when no local
    // OpenTelemetry collector is listening on :4318. Deployed metrics export is unaffected.
    systemProperty("management.otlp.metrics.export.enabled", "false")

    // Reshape the app's JSON logs into colorized console lines. This runs entirely inside Gradle
    // (using its bundled Groovy JSON parser) — no external tooling. Production logging is untouched;
    // this only affects the `./gradlew bootRun` console. Format: -Pdevlog=trim (default)|pretty|full|off.
    val devlogMode = (project.findProperty("devlog") as String?) ?: "trim"
    if (devlogMode != "off") {
        doFirst {
            val realOut = System.out
            standardOutput = object : OutputStream() {
                private val buf = ByteArrayOutputStream(256)

                override fun write(b: Int) {
                    if (b == '\n'.code) emit() else buf.write(b)
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    var start = off
                    val end = off + len
                    for (i in off until end) {
                        if (b[i] == '\n'.code.toByte()) {
                            buf.write(b, start, i - start)
                            emit()
                            start = i + 1
                        }
                    }
                    if (start < end) buf.write(b, start, end - start)
                }

                private fun emit() {
                    val line = buf.toString("UTF-8")
                    buf.reset()
                    realOut.println(colorizeLogLine(line, devlogMode))
                    realOut.flush()
                }

                override fun flush() {
                    realOut.flush()
                }
            }
        }
    }
}

/** Renders one logback JSON line as a colorized console line (see the `bootRun` task). */
fun colorizeLogLine(line: String, mode: String): String {
    val text = line.trimEnd('\r')
    if (!text.trimStart().startsWith("{")) return text
    val parsed = try {
        JsonSlurper().parseText(text)
    } catch (e: Exception) {
        return text
    }
    if (parsed !is Map<*, *>) return text

    val reset = "[0m"
    fun col(code: String, t: String) = "[${code}m$t$reset"
    val key = "36"; val str = "32"; val num = "33"; val const = "35"; val punct = "2"; val dim = "90"
    fun levelCol(lvl: String) = when (lvl.uppercase()) {
        "ERROR" -> "1;31"; "WARN", "WARNING" -> "1;33"; "INFO" -> "1;32"; "DEBUG" -> "36"; else -> "2"
    }
    fun jstr(v: String): String {
        val b = StringBuilder("\"")
        for (ch in v) when (ch) {
            '\\' -> b.append("\\\\"); '"' -> b.append("\\\""); '\n' -> b.append("\\n")
            '\r' -> b.append("\\r"); '\t' -> b.append("\\t")
            else -> if (ch < ' ') b.append("\\u%04x".format(ch.code)) else b.append(ch)
        }
        return b.append("\"").toString()
    }
    fun render(v: Any?): String = when (v) {
        null -> col(const, "null")
        is Map<*, *> -> if (v.isEmpty()) col(punct, "{}") else col(punct, "{") +
            v.entries.joinToString(col(punct, ", ")) { (k, vv) ->
                col(key, jstr(k.toString())) + col(punct, ":") + " " +
                    (if (k == "level" && vv is String) col(levelCol(vv), jstr(vv)) else render(vv))
            } + col(punct, "}")
        is List<*> -> if (v.isEmpty()) col(punct, "[]") else col(punct, "[") +
            v.joinToString(col(punct, ", ")) { render(it) } + col(punct, "]")
        is String -> col(str, jstr(v))
        is Boolean -> col(const, v.toString())
        is Number -> col(num, v.toString())
        else -> col(str, jstr(v.toString()))
    }
    if (mode == "full") return render(parsed)

    fun abbrev(name: String): String {
        val p = name.split(".")
        return if (p.size <= 1) name else p.dropLast(1).joinToString(".") { it.take(1) } + "." + p.last()
    }
    fun fmtTime(ms: Long): String =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
    fun applyArgs(msg: String, args: Any?): String {
        if (args !is List<*>) return msg
        var m = msg
        for (a in args) {
            if (!m.contains("{}")) break
            m = m.replaceFirst("{}", a.toString())
        }
        return m
    }
    fun kv(k: String, v: String) = col(key, jstr(k)) + col(punct, ":") + " " + v

    val fields = mutableListOf<String>()
    (parsed["timestamp"] as? Number)?.let { fields += kv("time", col(str, jstr(fmtTime(it.toLong())))) }
    (parsed["level"] as? String)?.let { fields += kv("level", col(levelCol(it), jstr(it))) }
    (parsed["loggerName"] as? String)?.let { fields += kv("logger", col(str, jstr(abbrev(it)))) }
    (parsed["message"] as? String)?.let {
        fields += kv("msg", col(str, jstr(applyArgs(it, parsed["arguments"]))))
    }
    (parsed["mdc"] as? Map<*, *>)?.takeIf { it.isNotEmpty() }?.let { fields += kv("mdc", render(it)) }
    val thr = parsed["throwable"] as? Map<*, *>
    if (thr != null) {
        fields += kv("error", col(str, jstr("${thr["className"]}: ${thr["message"] ?: ""}")))
    }

    val sb = StringBuilder()
    val isep = col(punct, ",") + if (mode == "pretty") "\n  " else " "
    sb.append(col(punct, "{")).append(if (mode == "pretty") "\n  " else "")
    sb.append(fields.joinToString(isep))
    sb.append(if (mode == "pretty") "\n" else "").append(col(punct, "}"))

    if (thr != null) {
        fun stack(t: Map<*, *>, depth: Int) {
            val ind = "    " + "  ".repeat(depth)
            sb.append("\n").append(
                col(dim, ind + (if (depth > 0) "caused by: " else "") + "${t["className"]}: ${t["message"] ?: ""}"),
            )
            val frames = t["stepArray"] as? List<*> ?: emptyList<Any?>()
            frames.take(30).forEach { f ->
                val fr = f as? Map<*, *> ?: return@forEach
                sb.append("\n").append(
                    col(dim, "$ind  at ${fr["className"]}.${fr["methodName"]}(${fr["fileName"]}:${fr["lineNumber"]})"),
                )
            }
            if (frames.size > 30) sb.append("\n").append(col(dim, "$ind  ... ${frames.size - 30} more"))
            (t["cause"] as? Map<*, *>)?.let { if (depth < 5) stack(it, depth + 1) }
        }
        stack(thr, 0)
    }
    return sb.toString()
}

springBoot {
    buildInfo()
    mainClass = "com.rightcrowd.sopstore.SopStoreApplication"
}

checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    configFile = rootDir.resolve("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
    // Gate the build: promote the Google ruleset's default 'warning' severity to 'error'.
    configProperties = mapOf("org.checkstyle.google.severity" to "error")
}

spotbugs {
    toolVersion = libs.versions.spotbugsTool.get()
    excludeFilter = rootDir.resolve("config/spotbugs/exclude.xml")
}

// SpotBugs 4.9.3 bundles ASM 9.7.1, which cannot read Java 25 (class file major
// version 69) bytecode. Force a newer ASM that supports it onto the analyzer's
// classpath. Remove once SpotBugs ships with ASM >= 9.8.
configurations.named("spotbugs") {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.ow2.asm") useVersion("9.10.1")
    }
}

tasks.named("bootBuildImage") {
    // configured in deploy/Dockerfile instead; native cnb build disabled
    enabled = false
}

// The UI is the standalone React + Blueprint SPA in web/ (Vite, built separately). The backend
// is a JSON API only — no server-rendered views, no bundled frontend assets.
