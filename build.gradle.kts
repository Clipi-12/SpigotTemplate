import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.io.IOException
import java.io.Serializable
import java.net.URI

plugins {
    java
    eclipse
    idea
    id("com.gradleup.shadow") version "8.3.0"
    id("com.github.gmazzo.buildconfig") version "5.3.5"
}

group = "me.dev_name"
version = "0.0.1"

val mainPackage = "${group}.${name.split(Regex("\\s+")).joinToString("_").lowercase()}"

val versionSpecificSources = sortedMapOf(
    *arrayOf(
        "1.17.1-R0.1-SNAPSHOT" to "pre_1_19",
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
    versionSpecificSources
        .entries
        .groupBy({ it.value }, { it.key })
        .forEach { (sourceSet, spigotVersions) ->
            fun disableSourceSetTasks(toBeDisabled: SourceSet) {
                arrayOf(
                    tasks.findByName(toBeDisabled.sourcesJarTaskName),
                    tasks.findByName(toBeDisabled.classesTaskName),
                    tasks.findByName(toBeDisabled.compileJavaTaskName),
                    tasks.findByName(toBeDisabled.jarTaskName),
                    tasks.findByName(toBeDisabled.javadocTaskName),
                    tasks.findByName(toBeDisabled.javadocJarTaskName),
                    tasks.findByName(toBeDisabled.processResourcesTaskName),
                ).filterNotNull().forEach {
                    it.dependsOn.clear()
                    it.enabled = false
                    it.group = "disabled"
                }
            }

            val compileClassPath = configurations.compileClasspath.get() - spigotLint

            disableSourceSetTasks(sourceSet)

            val sourceSetSpecificLint = configurations[sourceSet.compileOnlyConfigurationName]
            sourceSetSpecificLint(compileClassPath)
            sourceSetSpecificLint(spigotPrefix + spigotVersions.first())

            spigotVersions.forEachIndexed { index, spigotVersion ->
                val spigotSourceSet = sourceSets.create(spigotVersion)
                spigotSourceSet.java.srcDir(spigotSourceSet.java.sourceDirectories.map {
                    it.parentFile.resolve("${it.name}-forward-compatible")
                })
                disableSourceSetTasks(spigotSourceSet)

                val spigotSpecificLint = configurations[spigotSourceSet.compileOnlyConfigurationName]
                spigotSpecificLint(compileClassPath)
                spigotSpecificLint(spigotPrefix + spigotVersion)
                spigotSpecificLint(
                    classpathForVersion(versionSpecificSources.keys.indexOf(spigotVersion), null) - sourceSet.allSource
                )

                spigotSpecificLint(sourceSet.allSource.sourceDirectories)
                if (index == 0) sourceSetSpecificLint(spigotSourceSet.allSource.sourceDirectories)
            }

            sourceSetSpecificLint(
                classpathForVersion(versionSpecificSources.keys.indexOf(spigotVersions.first()), null)
                    - sourceSet.allSource
            )
        }
    // Lint with the oldest version to avoid unavailable APIs
    spigotLint(spigotPrefix + versionSpecificSources.keys.first())
    spigotLint(classpathForVersion(0, null) - sourceSets.main.get().allSource)

    // implementation("org.mongodb:mongodb-driver-sync:5.1.3") // Your dependencies

    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
}

javaToolchains.compilerFor {
    languageVersion = JavaLanguageVersion.of(maxOf(JavaVersion.VERSION_22, JavaVersion.current()).majorVersion.toInt())
}.let { compiler -> tasks.withType<JavaCompile> { javaCompiler = compiler } }

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

fun classpathForVersion(
    index: Int,
    configure: ((SourceSet) -> SourceDirectorySet)?,
): FileCollection {
    fun addToMap(map: MutableMap<File, File>, sourceSetDirs: Sequence<File>) {
        val temp = mutableMapOf<File, File>()
        sourceSetDirs.forEach { sourceSetDir ->
            sourceSetDir.walk()
                .filter { it.isFile }
                .map { it.relativeTo(sourceSetDir) }
                .forEach {
                    temp.putIfAbsent(it, sourceSetDir)?.let { previousDir ->
                        throw IOException("$it is contained in both $previousDir and $sourceSetDir")
                    }
                }
        }
        temp.forEach(map::putIfAbsent)
    }

    fun forwardCompatible(sourceSet: SourceDirectorySet) =
        sourceSet.sourceDirectories.asSequence().filter { it.name.endsWith("-forward-compatible") }

    val result = mutableMapOf<File, File>()
    val configurateOrAll = configure ?: { sSet -> sSet.allSource }
    versionSpecificSources.entries.elementAt(index).run {
        addToMap(result, configurateOrAll(sourceSets.getByName(key)).sourceDirectories.asSequence())
        addToMap(result, configurateOrAll(value).sourceDirectories.asSequence())
    }
    versionSpecificSources.keys
        .take(index)
        .asReversed().asSequence()
        .map { sourceSets.getByName(it) }
        .forEach { addToMap(result, forwardCompatible(configurateOrAll(it))) }
    addToMap(result, configurateOrAll(sourceSets.main.get()).sourceDirectories.asSequence())
    return if (configure == null) files(result.values.distinct()) else files(result.entries.map { it.value.resolve(it.key) }
        .toList())
}


tasks.assemble.get().dependsOn.clear()
tasks.check.get().dependsOn.clear()
Unit.run {
    val extraProps = project.properties - "properties" + props + mapOf(
        "mainPackage" to mainPackage,
        "bukkitProjectName" to name.split(Regex("\\s+")).joinToString("")
    )
    spigotVersions.forEachIndexed { index, (spigotVersion, majorVersion, majorMinorVersion) ->
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
            archiveExtension = Jar.DEFAULT_EXTENSION
            metadataCharset = "UTF-8"
            archiveClassifier = spigotVersion

            compileJava = spigotTask<JavaCompile>(spigotVersion, "compileJava") {
                source(classpathForVersion(index) { it.java })
                destinationDirectory = jarContentsTmp

                classpath = configurations.compileClasspath.get() - spigotLint + spigotDep

                options.run {
                    val buildToolsInfo = groovy.json.JsonSlurper().parseText(
                        URI("https://hub.spigotmc.org/versions/$majorMinorVersion.json").toURL().readText()
                    ) as Map<*, *>
                    val javaVersion = ((buildToolsInfo["javaVersions"] as List<*>?)?.get(0) as? Int)
                        ?.let(JavaVersion::forClassVersion)
                        ?: JavaVersion.VERSION_1_8

                    isFork = true
                    release = javaVersion.majorVersion.toInt()
                    if (index == 0)
                        java.toolchain.languageVersion = JavaLanguageVersion.of(release.get())

                    compilerArgs.add("-Xlint:all,-processing,-options")
                    if (project.findProperty("javac_error_on_warning").toString().toBoolean())
                        compilerArgs.add("-Werror")
                    encoding = "UTF-8"
                    annotationProcessorPath = configurations.annotationProcessor.get()
                }
            }

            processResources = spigotTask<Copy>(spigotVersion, "processResources") {
                from(classpathForVersion(index) { it.resources })
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

        val shadowJar = spigotTask<ShadowJar>(spigotVersion, "shadowJar") {
            dependsOn(compileJava, processResources)
            from(jarContentsTmp)
            destinationDirectory = layout.buildDirectory.dir("shadowJars")

            doFirst {
                minimize()
            }
            // Commented out until https://github.com/GradleUp/shadow/issues/955 is resolved
            // duplicatesStrategy = DuplicatesStrategy.FAIL
            isEnableRelocation = true
            relocationPrefix = "${mainPackage}.shaded"
            configurations += project.configurations.runtimeClasspath.get()
            archiveClassifier = spigotVersion
        }
        assemble.dependsOn(shadowJar)

        project.findProperty(majorMinorVersion + "_copy_dest")?.let {
            assemble.dependsOn(spigotTask<Copy>(spigotVersion, "copyResult") {
                dependsOn(shadowJar)
                from(shadowJar)
                destinationDir = file(it)
            })
        }
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
