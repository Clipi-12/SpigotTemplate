import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.io.Serializable
import java.net.URI

plugins {
    id("java")
    id("eclipse")
    id("idea")
    id("com.gradleup.shadow") version "8.3.0"
    id("com.github.gmazzo.buildconfig") version "5.3.5"
}

group = "me.dev_name"
version = "0.0.1"

val mainPackage = "${group}.${name.split(Regex("\\s+")).joinToString("_").lowercase()}"

val versionSpecificSources = sortedMapOf(
    *arrayOf(
        "1.18.2-R0.1-SNAPSHOT" to "pre_1_19",
        "1.19.3-R0.1-SNAPSHOT" to "1_19",
        "1.19.4-R0.1-SNAPSHOT" to "1_19",
        "1.21.1-R0.1-SNAPSHOT" to "post_1_19"
    )
        .map { (spigotVersion, sourceDir) -> spigotVersion to sourceSets.maybeCreate(sourceDir) }
        .toTypedArray()
)

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

repositories {
    mavenCentral()
    maven(url = "https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

val spigotLint: Configuration by configurations.creating
configurations.compileClasspath.get().extendsFrom(spigotLint)
val spigotPrefix = "org.spigotmc:spigot-api:"
val spigotVersions = versionSpecificSources.keys.map { spigotVersion ->
    val (majorMinorVersion, majorVersion) = Regex("""((\d+\.\d+)(?:\.\d+)?)-.+""").find(spigotVersion)!!.destructured
    Triple(spigotVersion, majorVersion, majorMinorVersion)
}
dependencies {
    // Lint with the oldest version to avoid unavailable APIs
    spigotLint(spigotPrefix + spigotVersions.first().component1())
    spigotLint(versionSpecificSources.values.first().allSource.sourceDirectories)
    versionSpecificSources
        .entries
        .reversed()
        .associate { (k, v) -> v to k }
        .forEach { (sourceSet, spigotVersion) ->
            arrayOf(
                tasks.findByName(sourceSet.sourcesJarTaskName),
                tasks.findByName(sourceSet.classesTaskName),
                tasks.findByName(sourceSet.compileJavaTaskName),
                tasks.findByName(sourceSet.jarTaskName),
                tasks.findByName(sourceSet.javadocTaskName),
                tasks.findByName(sourceSet.javadocJarTaskName),
                tasks.findByName(sourceSet.processResourcesTaskName),
            ).filterNotNull().forEach {
                it.dependsOn.clear()
                it.enabled = false
                it.group = "disabled"
            }

            val versionSpecificLint = configurations[sourceSet.compileOnlyConfigurationName]
            versionSpecificLint(configurations.compileClasspath.get() - spigotLint)
            versionSpecificLint(spigotPrefix + spigotVersion)
            versionSpecificLint(sourceSets.main.get().allSource.sourceDirectories)
        }

    // shadow("org.mongodb:mongodb-driver-sync:5.1.3") // Your dependencies

    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
}


java {
    toolchain.languageVersion.set(
        JavaLanguageVersion.of(maxOf(JavaVersion.VERSION_22, JavaVersion.current()).majorVersion.toInt())
    )
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

buildConfig {
    packageName("${mainPackage}.config")
    className("ConfigKeys")
    useJavaOutput()

    props.forEach { (key, value) ->
        buildConfigField(key) {
            type(value?.javaClass ?: Object::class.java)
            value(value)
        }
    }
}

inline fun <reified T : Task> spigotTask(
    spigotVersion: String,
    name: String,
    config: Action<in T> = Action {},
): T {
    return tasks.create<T>("${name}_$spigotVersion") {
        group = "z_$spigotVersion"
        config(this)
    }
}

tasks.assemble.get().dependsOn.clear()
tasks.check.get().dependsOn.clear()
Unit.run {
    val extraProps = project.properties - "properties" + props + mapOf(
        "mainPackage" to mainPackage,
        "bukkitProjectName" to name.split(Regex("\\s+")).joinToString("")
    )
    spigotVersions.forEach { (spigotVersion, majorVersion, majorMinorVersion) ->
        val spigotDep = configurations.create("spigot_dep_$spigotVersion") {
            this.dependencies.add(project.dependencies.create(spigotPrefix + spigotVersion))
        }

        val assemble = spigotTask<Task>(spigotVersion, "assemble")
        val check = spigotTask<Task>(spigotVersion, "check")
        tasks.assemble.get().dependsOn(assemble)
        tasks.check.get().dependsOn(check)
        spigotTask<Task>(spigotVersion, "build") {
            dependsOn(assemble, check)
        }

        lateinit var jarContentsTmp: File
        lateinit var compileJava: JavaCompile
        lateinit var processResources: Copy
        assemble.dependsOn(spigotTask<Zip>(spigotVersion, "jar") {
            jarContentsTmp = temporaryDir
            from(jarContentsTmp)
            archiveExtension.set(Jar.DEFAULT_EXTENSION)
            metadataCharset = "UTF-8"
            archiveClassifier.set(spigotVersion)

            compileJava = spigotTask<JavaCompile>(spigotVersion, "compileJava") {
                source(sourceSets.main.get().java, versionSpecificSources[spigotVersion]!!.java)
                destinationDirectory.set(jarContentsTmp)

                classpath = configurations.compileClasspath.get() - spigotLint + spigotDep
                javaCompiler.set(javaToolchains.compilerFor(java.toolchain))

                options.run {
                    val buildToolsInfo = groovy.json.JsonSlurper().parseText(
                        URI("https://hub.spigotmc.org/versions/$majorMinorVersion.json").toURL().readText()
                    ) as Map<*, *>
                    val javaVersion = ((buildToolsInfo["javaVersions"] as List<*>?)?.get(0) as? Int)
                        ?.let(JavaVersion::forClassVersion)
                        ?: JavaVersion.VERSION_1_8

                    isFork = true
                    release.set(javaVersion.majorVersion.toInt())

                    compilerArgs.add("-Xlint:all,-processing")
                    if (project.properties["javacWError"].toString().toBoolean()) compilerArgs.add("-Werror")
                    encoding = "UTF-8"
                    annotationProcessorPath = configurations.annotationProcessor.get()
                }
            }

            processResources = spigotTask<Copy>(spigotVersion, "processResources") {
                from(sourceSets.main.get().resources, versionSpecificSources[spigotVersion]!!.resources)
                into(jarContentsTmp)

                filteringCharset = "UTF-8"
                expand(
                    extraProps + mapOf(
                        "spigotVersion" to spigotVersion,
                        "apiVersion" to majorVersion,
                        "fullVersion" to majorMinorVersion,
                    )
                )
            }
            dependsOn(compileJava, processResources)
        })

        assemble.dependsOn(spigotTask<ShadowJar>(spigotVersion, "shadowJar") {
            dependsOn(compileJava, processResources)
            from(jarContentsTmp)
            destinationDirectory.set(layout.buildDirectory.dir("shadowJars"))

            isEnableRelocation = true
            relocationPrefix = "${mainPackage}.shaded"
            configurations += project.configurations.shadow.get()
            archiveClassifier.set(spigotVersion)
        })
    }
}

eclipse.classpath {
    isDownloadJavadoc = true
    isDownloadSources = true
}

idea.module {
    isDownloadJavadoc = true
    isDownloadSources = true
}
