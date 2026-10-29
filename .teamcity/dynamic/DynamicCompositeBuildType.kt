package dynamic

import jetbrains.buildServer.configs.kotlin.AbsoluteId
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings
import jetbrains.buildServer.configs.kotlin.Dependencies
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.IdOwner
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.pipelines.Pipeline
import java.util.LinkedList

open class DynamicCompositeBuildType(val project: Project) : BuildType() {
    // directory inside the versioned settings repository where the Kotlin DSL scripts are located
    var settingsDir: String = ".teamcity"
}

fun DynamicCompositeBuildType.whenTriggered(chainProducer: Dependencies.() -> Unit) {
    val chainId = id.toString()
    val chainIdContextParameter = "generate${chainId}"

    if (DslContext.hasParameter(chainIdContextParameter)) {
        dependencies {
            chainProducer()
        }

        val registeredObjects = findRegisteredObjects()

        // register all the dependencies in the project
        val visited = mutableSetOf<IdOwner>()
        val queue = LinkedList<IdOwner>()
        queue.add(this)

        while (!queue.isEmpty()) {
            val next = queue.pop()
            if (visited.add(next)) {
                val dependencies = when (next) {
                    is BuildType -> next.dependencies
                    is Pipeline -> next.dependencies
                    else -> error("Unsupported type: ${next::class}")
                }

                dependencies.items.forEach { dependency ->
                    val dep = dependency.buildTypeId
                    queue.add(dep)
                    if (registeredObjects.add(dep)) { // only register if not already registered
                        when (dep) {
                            is BuildType -> this.project.buildType(dep)
                            is Pipeline -> this.project.pipeline(dep)
                        }
                    }
                }
            }
        }
    }
}

private fun DynamicCompositeBuildType.findRegisteredObjects(): MutableSet<IdOwner> {
    val registeredObjects = mutableSetOf<IdOwner>()
    val projectsQueue = LinkedList<Project>()
    projectsQueue.add(project)

    while (!projectsQueue.isEmpty()) {
        val next = projectsQueue.pop()
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
    val chainId = dynamicCompositeBuildType.id.toString()
    val chainIdContextParameter = "generate${chainId}"
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

        steps {
            maven {
                goals = "clean teamcity-configs:generate"
                pomLocation = dynamicCompositeBuildType.settingsDirPath("pom.xml")
                runnerArgs = "-Dcontext.parentProjectId=${DslContext.parentProjectId} " +
                        "-Dcontext.projectId=${DslContext.projectId} " +
                        "\"-Dcontext.projectName=${DslContext.projectName}\" " +
                        "-Dcontext.settingsRootId=${DslContext.settingsRootId} " +
                        "-DenableSandbox=false " +
                        "-Dcontext.parameters.${chainIdContextParameter}=true"
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
