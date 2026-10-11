import dynamic.Util
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.FailureAction
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.project
import jetbrains.buildServer.configs.kotlin.version
import java.nio.file.Files
import java.nio.file.Path

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2026.1"

project {

    buildType(MainCheck)
    buildType(Generator)

    if (Util.isGeneratorMode()) {
        buildType(RunTests)
    }
}

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

object MainCheck : BuildType({
    name = "MainCheck"
    type = Type.COMPOSITE

    params {
        param("teamcity.dynamicBuildChain.generatorBuildTypeId", "DynamicBuildChains_Generator")
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    if (Util.isGeneratorMode()) {
        dependencies {
            snapshot(RunTests) {}
        }
    }
})

object RunTests: BuildType({
    name = "RunTests"

    vcs {
        DslContext.settingsRoot
    }

    steps {
        script {
            scriptContent = """
                echo "Running tests"
                echo "##teamcity[testStarted name='test0']"
                echo "##teamcity[testFinished name='test0']"
                echo "##teamcity[testStarted name='test1']"
                echo "##teamcity[testFinished name='test1']"
                echo "##teamcity[testStarted name='test2']"
                echo "##teamcity[testFinished name='test2']"
            """.trimIndent()
        }
    }
})
