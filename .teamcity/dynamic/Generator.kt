package dynamic

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.buildSteps.script

object Generator : BuildType({
    name = "Generator"

    artifactRules = ".teamcity/target/generated-configs/** => .teamcity/generated_settings.zip"

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        maven {
            id = "Maven2"
            goals = "clean teamcity-configs:generate"
            pomLocation = ".teamcity/pom.xml"
            runnerArgs = "-Dcontext.parentProjectId=${DslContext.parentProjectId} "
                    "-Dcontext.projectId=${DslContext.projectId} " +
                    "-Dcontext.projectName=${DslContext.projectName} "
                    "-Dcontext.settingsRootId=${DslContext.settingsRootId} "
                    "-DenableSandbox=false " +
                    "-Dcontext.parameters.generateMainCheckPipeline=true"
            workingDir = ".teamcity"
            jdkHome = "%teamcity.agent.jvm.java.home%"
        }
    }
})
