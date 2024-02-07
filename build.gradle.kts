import com.github.gmazzo.buildconfig.BuildConfigTask
import java.io.Serializable

plugins {
    id("java")
    id("eclipse")
    id("idea")
    id("com.github.gmazzo.buildconfig") version "5.3.5"
}

group = "me.dev_name"
version = "0.0.1"

val mainPackage = "${group}.${name.split(Regex("\\s+")).joinToString("_").lowercase()}"
project.extra["mainPackage"] = mainPackage
project.extra["bukkitProjectName"] = name.split(Regex("\\s+")).joinToString("")

repositories {
    mavenCentral()
    maven(url = "https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

var spigotConfLint = objects.sourceDirectorySet("spigotConfLint", "Spigot Configuration Lint")
val spigotDepLint: Configuration by configurations.creating
configurations.compileOnly.get().extendsFrom(spigotDepLint)
val spigotPrefix = "org.spigotmc:spigot-api:"
val spigotVersions = (project.property("spigotVersions") as String)
    .trim().split(Regex(""",\s*""")).toSortedSet()
    .map { spigotVersion -> spigotVersion to spigotVersion.substring(0, 4) }
dependencies {
    // Lint with the oldest version to avoid unavailable APIs
    spigotDepLint(spigotPrefix + spigotVersions.map { (spigotVersion, _) -> spigotVersion }.first())

    compileOnly("org.jetbrains:annotations:24.0.0")
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
}


val info: Map<String, Serializable?> = mapOf(
    "doWork_Name" to "do_work",
    "doWork_Aliases" to arrayOf("do_some_work", "do_tasks"),
)
// Providing config keys at compile-time ensures no typo-related error can happen
val configKeys: Map<String, Serializable?> = arrayOf(
    "working hours",
    "is working hard enough",
).associate { it ->
    val camelCase = it.split(" ")
        .joinToString("") { it.replaceFirstChar(Char::titlecaseChar) }
        .replaceFirstChar(Char::lowercaseChar)
    val snakeCase = it.split(" ").joinToString("_")
    "${camelCase}_Key" to snakeCase
}
val props = info + configKeys

val targetJavaVersion = 17
java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    if (JavaVersion.current() < javaVersion)
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
}

tasks.withType<JavaCompile>().configureEach {
    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible)
        options.release.set(targetJavaVersion)

    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}


inline fun <reified T : Task> spigotVersionTask(
    spigotVersion: String,
    name: String,
    config: Action<in T> = Action {}
): T {
    return tasks.create<T>("${name}_$spigotVersion") {
        group = "z_$spigotVersion"
        config(this)
    }
}

tasks.withType<BuildConfigTask>().forEach {
    it.enabled = false
}
buildConfig.sourceSets.clear()
tasks.build.get().dependsOn.clear()
spigotVersions.forEachIndexed { index, (spigotVersion, apiVersion) ->
    val spigotDep = configurations.create("spigot_dep_$spigotVersion") {
        this.dependencies.add(project.dependencies.create(spigotPrefix + spigotVersion))
    }

    val assemble = spigotVersionTask<Task>(spigotVersion, "assemble")
    val check = spigotVersionTask<Task>(spigotVersion, "check")

    lateinit var jarContentsTmp: File
    lateinit var generateBuildConfig: BuildConfigTask
    lateinit var compileJava: JavaCompile
    lateinit var processResources: Copy
    assemble.dependsOn(spigotVersionTask<Zip>(spigotVersion, "jar") {
        jarContentsTmp = temporaryDir
        from(jarContentsTmp)
        archiveExtension.set(Jar.DEFAULT_EXTENSION)
        metadataCharset = "UTF-8"
        archiveClassifier.set(spigotVersion)

        generateBuildConfig = buildConfig.sourceSets.create(spigotVersion) {
            packageName("${mainPackage}.config")
            className("ConfigKeys")
            useJavaOutput()

            props.forEach { (key, value) ->
                buildConfigField(key) {
                    type(value?.javaClass ?: Object::class.java)
                    value(value)
                }
            }

            forClass("CompilationInfo") {
                buildConfigField("apiVersion", apiVersion)
            }
        }.generateTask.get().also {
            it.group = "z_$spigotVersion"
            if (index == 0) {
                spigotConfLint.srcDir(it)
                sourceSets.main.configure { java.srcDir(spigotConfLint) }
            }
        }

        compileJava = spigotVersionTask<JavaCompile>(spigotVersion, "compileJava") {
            source(sourceSets.main.map { it.java - spigotConfLint }, generateBuildConfig)
            destinationDirectory.set(jarContentsTmp)

            options.encoding = "UTF-8"
            classpath = configurations.compileClasspath.get() - spigotDepLint + spigotDep
            options.annotationProcessorPath = configurations.annotationProcessor.get()

            dependsOn(generateBuildConfig)
        }

        processResources = spigotVersionTask<Copy>(spigotVersion, "processResources") {
            from(sourceSets.main.map { it.resources })
            into(jarContentsTmp)

            val extraProps = project.properties - "properties" + project.extra.properties + props + mapOf(
                "spigotVersion" to spigotVersion,
                "apiVersion" to apiVersion,
            )
            filteringCharset = "UTF-8"
            expand(extraProps)
        }

        dependsOn(compileJava, processResources)
    })

    tasks.build.get().finalizedBy(spigotVersionTask<Task>(spigotVersion, "build") {
        dependsOn(assemble, check)
    })
}

eclipse.classpath {
    isDownloadJavadoc = true
    isDownloadSources = true
}

idea.module {
    isDownloadJavadoc = true
    isDownloadSources = true
}
