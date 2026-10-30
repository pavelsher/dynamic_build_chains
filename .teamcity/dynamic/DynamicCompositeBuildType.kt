package dynamic

import jetbrains.buildServer.configs.kotlin.AbsoluteId
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings
import jetbrains.buildServer.configs.kotlin.Dependencies
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.IdOwner
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.PropInitOnceDelegate
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.pipelines.Pipeline
import java.util.ArrayDeque

open class DynamicCompositeBuildType(val project: Project) : BuildType() {
    // directory inside the versioned settings repository where the Kotlin DSL scripts are located
    var settingsDir: String = ".teamcity"
}

private const val DYNAMIC_BUILD_TYPE_ID_ENV_VAR = "DYNAMIC_BUILD_TYPE_ID"

fun DynamicCompositeBuildType.whenTriggered(chainProducer: Dependencies.() -> Unit) {
    if (id.toString() != System.getenv(DYNAMIC_BUILD_TYPE_ID_ENV_VAR)) return

    PropInitOnceDelegate(this).setInitializer {
        // we should call the chain producer when all the projects are initialized
        // this ensures that it will see all the registered objects properly
        applyWhenTriggered(chainProducer)
    }
}

private fun DynamicCompositeBuildType.applyWhenTriggered(chainProducer: Dependencies.() -> Unit) {
    dependencies {
        chainProducer()
    }

    val registeredObjects = findRegisteredObjects()

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

private fun DynamicCompositeBuildType.findRegisteredObjects(): MutableSet<IdOwner> {
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

fun Project.dynamicCompositeBuildType(init: DynamicCompositeBuildType.() -> Unit): DynamicCompositeBuildType {
    val dynamicCompositeBuildType = DynamicCompositeBuildType(this)
    dynamicCompositeBuildType.init()
    dynamicCompositeBuildType.type = BuildTypeSettings.Type.COMPOSITE

    val generatorId = dynamicCompositeBuildType.id.toString() + "Generator"
    dynamicCompositeBuildType.params {
        param("teamcity.dynamicBuildChain.generatorBuildTypeId", generatorId)
    }

    buildType(dynamicCompositeBuildType)

    // create generator build type
    buildType {
        id = AbsoluteId(generatorId)
        name = dynamicCompositeBuildType.name + " Generator"

        artifactRules = dynamicCompositeBuildType.settingsDirPath("target/generated-configs/**") + " => .teamcity/generated_settings.zip"

        vcs {
            root(DslContext.settingsRoot)
        }

        params {
            // add marker environment variable which tells us that it's time to generate a dynamic part
            param("env.$DYNAMIC_BUILD_TYPE_ID_ENV_VAR", dynamicCompositeBuildType.id.toString())
        }

        steps {
            maven {
                goals = "clean teamcity-configs:generate"
                pomLocation = dynamicCompositeBuildType.settingsDirPath("pom.xml")
                runnerArgs = "-Dcontext.parentProjectId=${DslContext.parentProjectId} " +
                        "-Dcontext.projectId=${DslContext.projectId} " +
                        "\"-Dcontext.projectName=${DslContext.projectName}\" " +
                        "-Dcontext.settingsRootId=${DslContext.settingsRootId} " +
                        "-DenableSandbox=false"
                workingDir = dynamicCompositeBuildType.settingsDirPath("")
                jdkHome = "%teamcity.agent.jvm.java.home%"
            }
        }
    }

    return dynamicCompositeBuildType
}

private fun DynamicCompositeBuildType.settingsDirPath(relPath: String): String {
    if (relPath.isEmpty() || settingsDir.isEmpty()) return relPath
    if (settingsDir.endsWith("/")) return settingsDir + relPath
    return "$settingsDir/$relPath"
}
