package dynamic

import java.nio.file.Files
import java.nio.file.Path

class Util {
    companion object {
        fun isGeneratorMode() = Files.exists(Path.of("GeneratorMode"))
    }
}