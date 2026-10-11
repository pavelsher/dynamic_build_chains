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
        script {
            id = "simpleRunner"
            scriptContent = """curl -u admin:1 "http://localhost:8111/bs/app/dsl-context?projectExtId=DynamicBuildChains" -o .teamcity/dsl_context.zip"""
        }
        script {
            id = "simpleRunner_1"
            scriptContent = """echo "GeneratorMode" > .teamcity/GeneratorMode"""
        }
        maven {
            id = "Maven2"
            goals = "clean teamcity-configs:generate"
            pomLocation = ".teamcity/pom.xml"
            runnerArgs = "-DserverContext=dsl_context.zip"
            workingDir = ".teamcity"
            jdkHome = "%teamcity.agent.jvm.java.home%"
        }
    }
})