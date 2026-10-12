package dynamic

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.DslContext

object MainCheck : BuildType({
    name = "MainCheck"
    type = Type.COMPOSITE

    params {
        param("teamcity.dynamicBuildChain.generatorBuildTypeId", Generator.id!!.value)
        param("override.dep.*!.p1", "v1")
    }

    vcs {
        root(DslContext.settingsRoot)
    }

})
