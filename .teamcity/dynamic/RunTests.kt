package dynamic

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.buildSteps.script

class RunTests(name: String): BuildType({
    this.name = name
    this.id(name)

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        script {
            scriptContent = """
                echo "Running tests batch $name"
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

fun createBatches(num: Int): List<RunTests> {
    return (0 until num).map { RunTests("TestsBatch$it") }
}

fun applyBatches(project: Project) {
    val batches = createBatches(2)
    batches.forEach { project.buildType(it) }
    MainCheck.dependencies {
        batches.forEach { snapshot(it) {} }
    }
}

fun applyMatrixBuild(project: Project) {
    val matrix = MatrixBuild()
    project.buildType(matrix)
    MainCheck.dependencies {
        snapshot(matrix) {}
    }
}

fun applyPipeline(project: Project) {
    val pipeline = project.pipeline {
        id("Pipeline")
        name = "Test Pipeline"

        job {
            id("Job")
            name = "Test Job"

            steps {
                script {
                    scriptContent = """
                        echo "Hello pipeline!"
                    """.trimIndent()
                }
            }
        }
    }

    MainCheck.dependencies {
        snapshot(pipeline) {}
    }
}