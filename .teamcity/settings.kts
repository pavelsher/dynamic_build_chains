import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script

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
}

object Generator : BuildType({
    name = "Generator"

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        script {
            id = "simpleRunner"
            scriptContent = """curl -u admin:1 "http://localhost:8111/bs/app/dsl-context?projectExtId=DynamicChains" -o .teamcity/dsl_context.zip"""
        }
        script {
            id = "simpleRunner_1"
            scriptContent = """echo "GeneratorMode" > .teamcity/GeneratorMode"""
        }
    }
})

object MainCheck : BuildType({
    name = "MainCheck"

    params {
        param("teamcity.dynamicBuildChain.generatorBuildTypeId", "DynamicBuildChains_Generator")
    }

    vcs {
        root(DslContext.settingsRoot)
    }
})
