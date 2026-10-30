import dynamic.MatrixBuild
import dynamic.createBatches
import dynamic.dynamicBuildChain
import dynamic.whenTriggered
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.pipelines.Pipeline
import jetbrains.buildServer.configs.kotlin.project
import jetbrains.buildServer.configs.kotlin.version

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
    dynamicBuildChain {
        id("MainCheck")

        name = "Main Check"
        description = "Runs the main check"
        settingsDir = ".teamcity"

        vcs {
            root(DslContext.settingsRoot, "+:${this@dynamicBuildChain.settingsDir}")
        }
    }.whenTriggered {
        snapshot(BuildType {
            id("TestsAggregator")
            name = "Tests Aggregator"
            type = BuildTypeSettings.Type.COMPOSITE

            vcs {
                root(DslContext.settingsRoot)
            }

            dependencies {
                createBatches(5).forEach { snapshot(it) {} }
            }

        }) {}

        snapshot(MatrixBuild) {}

        snapshot(Pipeline {
            id("Pipeline")
            name = "Test Pipeline"

            repositories {
                repository(DslContext.settingsRoot, enabledByDefault = true)
            }

            job {
                id("Compile")
                name = "Compile"

                steps {
                    script {
                        scriptContent = """
                        echo "Compiling"
                    """.trimIndent()
                    }
                }
            }

            job {
                id("RunTests")
                name = "Run Tests"

                steps {
                    script {
                        scriptContent = """
                        echo "running tests"
                        echo "##teamcity[testStarted name='pipeline.test0']"
                        echo "##teamcity[testFinished name='pipeline.test0']"
                        echo "##teamcity[testStarted name='pipeline.test1']"
                        echo "##teamcity[testFinished name='pipeline.test1']"
                        echo "##teamcity[testStarted name='pipeline.test2']"
                        echo "##teamcity[testFinished name='pipeline.test2']"
                    """.trimIndent()
                    }
                }

                dependency("Compile") {}
            }
        }) {}
    }

    subProject {
        id("Matrix")
        name = "Matrix"

        buildType(MatrixBuild)
    }
}
