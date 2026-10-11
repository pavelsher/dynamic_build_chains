package dynamic

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.DslContext

object MainCheck : BuildType({
    name = "MainCheck"
    type = Type.COMPOSITE

    params {
        param("teamcity.dynamicBuildChain.generatorBuildTypeId", "DynamicBuildChains_Generator")
    }

    vcs {
        root(DslContext.settingsRoot)
    }
})