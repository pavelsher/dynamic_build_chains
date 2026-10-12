package dynamic

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.matrix

class MatrixBuild: BuildType({
    name = "Matrix Build"

    features {
        matrix {
            param("database.image", listOf(
                value("mysql"),
                value("psql")
            ))
        }
    }
})