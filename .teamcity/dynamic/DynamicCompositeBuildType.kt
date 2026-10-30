package dynamic

import jetbrains.buildServer.configs.kotlin.AbsoluteId
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings
import jetbrains.buildServer.configs.kotlin.Dependencies
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.IdOwner
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.buildFeatures.buildCache
import jetbrains.buildServer.configs.kotlin.buildSteps.MavenBuildStep
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.pipelines.Pipeline
import java.util.ArrayDeque

private const val DYNAMIC_BUILD_TYPE_ID_ENV_VAR = "DYNAMIC_BUILD_TYPE_ID"

open class DynamicCompositeBuildType: BuildType() {
    // directory inside the versioned settings repository where the Kotlin DSL scripts are located
    var settingsDir: String = ".teamcity"
    var generator: BuildType? = null
    private var dynamicDependenciesInitializer: Dependencies.() -> Unit = {}

    override fun initializeInProject(project: Project) {
        if (this.generator == null) {
            throw IllegalStateException("The build configuration $this does not have a generator")
        }

        project.buildType(generator!!)

        applyDynamicDependenciesInitializer(project, dynamicDependenciesInitializer)
    }

    fun dynamicDependencies(chainProducer: Dependencies.() -> Unit) {
        if (id.toString() != System.getenv(DYNAMIC_BUILD_TYPE_ID_ENV_VAR)) return

        dynamicDependenciesInitializer = chainProducer
    }

    private fun applyDynamicDependenciesInitializer(project: Project, chainProducer: Dependencies.() -> Unit) {
        dependencies {
            chainProducer()
        }

        val registeredObjects = findRegisteredObjects(project)

        // register all the dependencies in the project
        val visited = mutableSetOf<IdOwner>()
        val queue = ArrayDeque<IdOwner>()
        queue.add(this)

        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (!visited.add(next)) continue

            val dependencies = when (next) {
                is BuildType -> next.dependencies
                is Pipeline -> next.dependencies
                else -> error("Unsupported type: ${next::class}")
            }

            for (dependency in dependencies.items) {
                val target = dependency.buildTypeId
                queue.add(target)

                if (registeredObjects.add(target)) {
                    when (target) {
                        is BuildType -> project.buildType(target)
                        is Pipeline -> project.pipeline(target)
                    }
                }
            }
        }
    }

    private fun findRegisteredObjects(project: Project): MutableSet<IdOwner> {
        val registeredObjects = mutableSetOf<IdOwner>()
        val projectsQueue = ArrayDeque<Project>()
        projectsQueue.add(project)

        while (projectsQueue.isNotEmpty()) {
            val next = projectsQueue.removeFirst()
            registeredObjects.addAll(next.buildTypes)
            registeredObjects.addAll(next.pipelines)
            projectsQueue.addAll(next.subProjects)
        }
        return registeredObjects
    }
}

fun Project.dynamicBuildChain(init: DynamicCompositeBuildType.() -> Unit): DynamicCompositeBuildType {
    val dynamicHead = DynamicCompositeBuildType()
    dynamicHead.init()
    dynamicHead.type = BuildTypeSettings.Type.COMPOSITE

    val generatorId = dynamicHead.id.toString() + "Generator"
    dynamicHead.params {
        param("teamcity.dynamicBuildChain.generatorBuildTypeId", generatorId)
    }

    buildType(dynamicHead)

    // create generator build type
    dynamicHead.generator = BuildType() {
        id = AbsoluteId(generatorId)
        name = dynamicHead.name + " Generator"

        artifactRules = dynamicHead.settingsDirPath("target/generated-configs/**") + " => .teamcity/generated_settings.zip"

        vcs {
            // generator gets the same roots and checkout rules as the chain head
            for ((root, checkoutRules) in dynamicHead.vcs.entries) {
                root(root.id!!, checkoutRules.joinToString("\n"))
            }
        }

        params {
            // add marker environment variable which tells us that it's time to generate a dynamic part
            param("env.$DYNAMIC_BUILD_TYPE_ID_ENV_VAR", dynamicHead.id.toString())
        }

        val mavenRepoDir = "%teamcity.build.checkoutDir%/.m2"
        val kotlinIncrementalCacheDir = dynamicHead.settingsDirPath("target/kotlin-dsl-ic")

        steps {
            maven {
                goals = "clean teamcity-configs:generate"
                pomLocation = dynamicHead.settingsDirPath("pom.xml")
                runnerArgs = "-Dcontext.parentProjectId=${DslContext.parentProjectId} " +
                        "-Dcontext.projectId=${DslContext.projectId} " +
                        "\"-Dcontext.projectName=${DslContext.projectName}\" " +
                        "-Dcontext.settingsRootId=${DslContext.settingsRootId} " +
                        "-DenableSandbox=false " +
                        "-Dkotlin.compiler.incremental=true " +
                        "\"-Dmaven.repo.local=${mavenRepoDir}\""
                localRepoScope = MavenBuildStep.RepositoryScope.MAVEN_DEFAULT
                mavenVersion = bundled_3_9()
                workingDir = dynamicHead.settingsDirPath("")
                jdkHome = "%teamcity.agent.jvm.java.home%"
            }
        }

        features {
            buildCache {
                name = "maven_repo"
                rules = """
                    $mavenRepoDir
                    $kotlinIncrementalCacheDir
                """.trimIndent()
                publishOnlyChanged = true
            }
        }
    }

    return dynamicHead
}

private fun DynamicCompositeBuildType.settingsDirPath(relPath: String): String {
    if (relPath.isEmpty() || settingsDir.isEmpty()) return relPath
    if (settingsDir.endsWith("/")) return settingsDir + relPath
    return "$settingsDir/$relPath"
}
