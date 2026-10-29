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

open class DynamicBuildChain(val project: Project) : BuildType()

fun DynamicBuildChain.whenTriggered(chainProducer: Dependencies.() -> Unit) {
    val chainId = id.toString()
    val chainIdContextParameter = "generate${chainId}"

    if (DslContext.hasParameter(chainIdContextParameter)) {
        dependencies {
            chainProducer()
        }

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
                    when (dep) {
                        is BuildType -> this.project.buildType(dep)
                        is Pipeline -> this.project.pipeline(dep)
                    }
                }
            }
        }
    }
}

fun Project.dynamicCompositeBuildType(init: DynamicBuildChain.() -> Unit): DynamicBuildChain {
    val dynamicBuildChain = DynamicBuildChain(this)
    dynamicBuildChain.init()
    dynamicBuildChain.type = BuildTypeSettings.Type.COMPOSITE

    val generatorId = dynamicBuildChain.id.toString() + "Generator"
    val chainId = dynamicBuildChain.id.toString()
    val chainIdContextParameter = "generate${chainId}"
    dynamicBuildChain.params {
        param("teamcity.dynamicBuildChain.generatorBuildTypeId", generatorId)
    }

    buildType(dynamicBuildChain)

    // create generator build type
    buildType {
        id = AbsoluteId(generatorId)
        name = dynamicBuildChain.name + " Generator"

        artifactRules = ".teamcity/target/generated-configs/** => .teamcity/generated_settings.zip"

        vcs {
            root(DslContext.settingsRoot)
        }

        steps {
            maven {
                goals = "clean teamcity-configs:generate"
                pomLocation = ".teamcity/pom.xml"
                runnerArgs = "-Dcontext.parentProjectId=${DslContext.parentProjectId} " +
                        "-Dcontext.projectId=${DslContext.projectId} " +
                        "\"-Dcontext.projectName=${DslContext.projectName}\" " +
                        "-Dcontext.settingsRootId=${DslContext.settingsRootId} " +
                        "-DenableSandbox=false " +
                        "-Dcontext.parameters.${chainIdContextParameter}=true"
                workingDir = ".teamcity"
                jdkHome = "%teamcity.agent.jvm.java.home%"
            }
        }
    }

    return dynamicBuildChain
}

