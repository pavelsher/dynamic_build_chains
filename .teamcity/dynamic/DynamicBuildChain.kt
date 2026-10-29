package dynamic

import jetbrains.buildServer.configs.kotlin.AbsoluteId
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings
import jetbrains.buildServer.configs.kotlin.Dependencies
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import java.util.LinkedList

fun Project.dynamicBuildChain(init: BuildType.() -> Unit, chainProducer: Dependencies.() -> Unit): BuildType {
    val dynamicBuildChain = buildType(init)
    dynamicBuildChain.type = BuildTypeSettings.Type.COMPOSITE

    val chainId = dynamicBuildChain.id.toString()
    val generatorId = dynamicBuildChain.id.toString() + "Generator"
    val chainIdContextParameter = "generate${chainId}"
    dynamicBuildChain.params {
        param("teamcity.dynamicBuildChain.generatorBuildTypeId", generatorId)
    }

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

    if (DslContext.hasParameter(chainIdContextParameter)) {
        chainProducer(dynamicBuildChain.dependencies)

        val project = this

        // register all the dependencies in the project
        val visited = mutableSetOf<BuildType>()
        val queue = LinkedList<BuildType>()
        queue.add(dynamicBuildChain)

        while (!queue.isEmpty()) {
            val next = queue.pop()
            if (visited.add(next)) {
                next.dependencies.items.forEach { dependency ->
                    val depBuildType = dependency.buildTypeId
                    if (depBuildType is BuildType) {
                        project.buildType(depBuildType)
                        queue.add(depBuildType)
                    }
                }
            }
        }
    }

    return dynamicBuildChain
}
