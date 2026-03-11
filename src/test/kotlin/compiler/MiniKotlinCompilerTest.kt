package org.example.compiler

import MiniKotlinLexer
import MiniKotlinParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MiniKotlinCompilerTest {

    @TempDir
    lateinit var tempDir: Path

    private fun parseString(source: String): MiniKotlinParser.ProgramContext {
        val input = CharStreams.fromString(source)
        val lexer = MiniKotlinLexer(input)
        val tokens = CommonTokenStream(lexer)
        val parser = MiniKotlinParser(tokens)
        return parser.program()
    }

    private fun parseFile(path: Path): MiniKotlinParser.ProgramContext {
        val input = CharStreams.fromPath(path)
        val lexer = MiniKotlinLexer(input)
        val tokens = CommonTokenStream(lexer)
        val parser = MiniKotlinParser(tokens)
        return parser.program()
    }

    private fun resolveStdlibPath(): Path? {
        val devPath = Paths.get("build", "stdlib")
        if (devPath.toFile().exists()) {
            val stdlibJar = devPath.toFile().listFiles()
                ?.firstOrNull { it.name.startsWith("stdlib") && it.name.endsWith(".jar") }
            if (stdlibJar != null) return stdlibJar.toPath()
        }
        return null
    }

    @Test
    fun `compile example_mini outputs 120 and 15`() {
        val examplePath = Paths.get("samples/example.mini")
        val program = parseFile(examplePath)

        val compiler = MiniKotlinCompiler()
        val javaCode = compiler.compile(program)

        val javaFile = tempDir.resolve("MiniProgram.java")
        Files.writeString(javaFile, javaCode)

        val javaCompiler = JavaRuntimeCompiler()
        val stdlibPath = resolveStdlibPath()
        val (compilationResult, executionResult) = javaCompiler.compileAndExecute(javaFile, stdlibPath)

        assertIs<CompilationResult.Success>(compilationResult)
        assertIs<ExecutionResult.Success>(executionResult)

        val output = executionResult.stdout
        assertTrue(output.contains("120"), "Expected output to contain factorial result 120, but got: $output")
        assertTrue(output.contains("15"), "Expected output to contain arithmetic result 15, but got: $output")
    }

    @Test
    fun whileLoopTest() {
        val source = """
        fun main(): Unit {
            var i: Int = 0
            while (i < 3) {
                i = i + 1
                println(i)
            }
            println(i)
        }
        """

        val stream = CharStreams.fromString(source)
        val lexer = MiniKotlinLexer(stream)
        val tokens = CommonTokenStream(lexer)
        val parser = MiniKotlinParser(tokens)

        val compiler = MiniKotlinCompiler()
        val javaCode = compiler.compile(parser.program())

        val javaFile = tempDir.resolve("MiniProgram.java")
        Files.writeString(javaFile, javaCode)

        val javaCompiler = JavaRuntimeCompiler()
        val stdlibPath = resolveStdlibPath()
        val (compilationResult, executionResult) = javaCompiler.compileAndExecute(javaFile, stdlibPath)

        assertIs<CompilationResult.Success>(compilationResult)
        assertIs<ExecutionResult.Success>(executionResult)

        val output = executionResult.stdout
        assertTrue(output.contains("1"))
        assertTrue(output.contains("2"))
        assertTrue(output.contains("3"))
    }

    @Test
    fun nestedIf() {
        val source = """
        fun main(): Unit {
            var a: Int = 15

            if (a > 10) {
                if (a > 20) {
                    println(20)
                } else {
                    println(10)
                }
            } else {
                println(0)
            }
        }
        """

        val stream = CharStreams.fromString(source)
        val lexer = MiniKotlinLexer(stream)
        val tokens = CommonTokenStream(lexer)
        val parser = MiniKotlinParser(tokens)

        val compiler = MiniKotlinCompiler()
        val javaCode = compiler.compile(parser.program())

        val javaFile = tempDir.resolve("MiniProgram.java")
        Files.writeString(javaFile, javaCode)

        val javaCompiler = JavaRuntimeCompiler()
        val stdlibPath = resolveStdlibPath()
        val (compilationResult, executionResult) = javaCompiler.compileAndExecute(javaFile, stdlibPath)

        assertIs<CompilationResult.Success>(compilationResult)
        assertIs<ExecutionResult.Success>(executionResult)

        val output = executionResult.stdout
        assertTrue(output.contains("10"))
    }

    @Test
    fun stringReturn() {
        val source = """
        fun format(name: String): String {
            return "Hello " + name
        }

        fun main(): Unit {
            var message: String = format("World")
            println(message)
        }
        """

        val stream = CharStreams.fromString(source)
        val lexer = MiniKotlinLexer(stream)
        val tokens = CommonTokenStream(lexer)
        val parser = MiniKotlinParser(tokens)

        val compiler = MiniKotlinCompiler()
        val javaCode = compiler.compile(parser.program())

        val javaFile = tempDir.resolve("MiniProgram.java")
        Files.writeString(javaFile, javaCode)

        val javaCompiler = JavaRuntimeCompiler()
        val stdlibPath = resolveStdlibPath()
        val (compilationResult, executionResult) = javaCompiler.compileAndExecute(javaFile, stdlibPath)

        assertIs<CompilationResult.Success>(compilationResult)
        assertIs<ExecutionResult.Success>(executionResult)

        val output = executionResult.stdout
        assertTrue(output.contains("Hello World"))
    }

    @Test
    fun unitReturn() {
        val source = """
        fun printSum(a: Int, b: Int): Unit {
            println(a + b)
        }

        fun main(): Unit {
            printSum(3, 4)
        }
        """

        val stream = CharStreams.fromString(source)
        val lexer = MiniKotlinLexer(stream)
        val tokens = CommonTokenStream(lexer)
        val parser = MiniKotlinParser(tokens)

        val compiler = MiniKotlinCompiler()
        val javaCode = compiler.compile(parser.program())

        val javaFile = tempDir.resolve("MiniProgram.java")
        Files.writeString(javaFile, javaCode)

        val javaCompiler = JavaRuntimeCompiler()
        val stdlibPath = resolveStdlibPath()
        val (compilationResult, executionResult) = javaCompiler.compileAndExecute(javaFile, stdlibPath)

        assertIs<CompilationResult.Success>(compilationResult)
        assertIs<ExecutionResult.Success>(executionResult)

        val output = executionResult.stdout
        assertTrue(output.contains("7"))
    }

    @Test
    fun booleanReturn() {
        val source = """
        fun isPositive(n: Int): Boolean {
            if (n > 0) {
                return true
            } else {
                return false
            }
        }

        fun main(): Unit {
            var result: Boolean = isPositive(10)
            println(result)
        }
        """

        val stream = CharStreams.fromString(source)
        val lexer = MiniKotlinLexer(stream)
        val tokens = CommonTokenStream(lexer)
        val parser = MiniKotlinParser(tokens)

        val compiler = MiniKotlinCompiler()
        val javaCode = compiler.compile(parser.program())

        val javaFile = tempDir.resolve("MiniProgram.java")
        Files.writeString(javaFile, javaCode)

        val javaCompiler = JavaRuntimeCompiler()
        val stdlibPath = resolveStdlibPath()
        val (compilationResult, executionResult) = javaCompiler.compileAndExecute(javaFile, stdlibPath)

        assertIs<CompilationResult.Success>(compilationResult)
        assertIs<ExecutionResult.Success>(executionResult)

        val output = executionResult.stdout
        assertTrue(output.contains("true"))
    }

    @Test
    fun recursion() {
        val source = """
        fun fib(n: Int): Int {
            if (n <= 1) {
                return n
            } else {
                return fib(n - 1) + fib(n - 2)
            }
        }

        fun main(): Unit {
            var x: Int = fib(6)
            println(x)
        }
        """

        val stream = CharStreams.fromString(source)
        val lexer = MiniKotlinLexer(stream)
        val tokens = CommonTokenStream(lexer)
        val parser = MiniKotlinParser(tokens)

        val compiler = MiniKotlinCompiler()
        val javaCode = compiler.compile(parser.program())

        val javaFile = tempDir.resolve("MiniProgram.java")
        Files.writeString(javaFile, javaCode)

        val javaCompiler = JavaRuntimeCompiler()
        val stdlibPath = resolveStdlibPath()
        val (compilationResult, executionResult) = javaCompiler.compileAndExecute(javaFile, stdlibPath)

        assertIs<CompilationResult.Success>(compilationResult)
        assertIs<ExecutionResult.Success>(executionResult)

        val output = executionResult.stdout
        assertTrue(output.contains("8"))
    }

    @Test
    fun mixedConditionals() {
        val source = """
        fun main(): Unit {
            var i: Int = 0

            while (i < 5) {
                if (i % 2 == 0) {
                    println(i)
                }
                i = i + 1
            }
        }
        """

        val stream = CharStreams.fromString(source)
        val lexer = MiniKotlinLexer(stream)
        val tokens = CommonTokenStream(lexer)
        val parser = MiniKotlinParser(tokens)

        val compiler = MiniKotlinCompiler()
        val javaCode = compiler.compile(parser.program())

        val javaFile = tempDir.resolve("MiniProgram.java")
        Files.writeString(javaFile, javaCode)

        val javaCompiler = JavaRuntimeCompiler()
        val stdlibPath = resolveStdlibPath()
        val (compilationResult, executionResult) = javaCompiler.compileAndExecute(javaFile, stdlibPath)

        assertIs<CompilationResult.Success>(compilationResult)
        assertIs<ExecutionResult.Success>(executionResult)

        val output = executionResult.stdout
        assertTrue(output.contains("0"))
        assertTrue(output.contains("2"))
        assertTrue(output.contains("4"))
    }

    @Test
    fun multipleParams() {
        val source = """
        fun add(a: Int, b: Int): Int {
            return a + b
        }

        fun main(): Unit {
            var result: Int = add(3, 4)
            println(result)
        }
        """

        val stream = CharStreams.fromString(source)
        val lexer = MiniKotlinLexer(stream)
        val tokens = CommonTokenStream(lexer)
        val parser = MiniKotlinParser(tokens)

        val compiler = MiniKotlinCompiler()
        val javaCode = compiler.compile(parser.program())

        val javaFile = tempDir.resolve("MiniProgram.java")
        Files.writeString(javaFile, javaCode)

        val javaCompiler = JavaRuntimeCompiler()
        val stdlibPath = resolveStdlibPath()
        val (compilationResult, executionResult) = javaCompiler.compileAndExecute(javaFile, stdlibPath)

        assertIs<CompilationResult.Success>(compilationResult)
        assertIs<ExecutionResult.Success>(executionResult)

        val output = executionResult.stdout
        assertTrue(output.contains("7"))
    }

    @Test
    fun moreFunctions() {
        val source = """
        fun square(x: Int): Int {
            return x * x
        }

        fun double_(x: Int): Int {
            return x + x
        }

        fun main(): Unit {
            var a: Int = square(5)
            var b: Int = double_(a)

            println(b)
        }
        """

        val stream = CharStreams.fromString(source)
        val lexer = MiniKotlinLexer(stream)
        val tokens = CommonTokenStream(lexer)
        val parser = MiniKotlinParser(tokens)

        val compiler = MiniKotlinCompiler()
        val javaCode = compiler.compile(parser.program())

        val javaFile = tempDir.resolve("MiniProgram.java")
        Files.writeString(javaFile, javaCode)

        val javaCompiler = JavaRuntimeCompiler()
        val stdlibPath = resolveStdlibPath()
        val (compilationResult, executionResult) = javaCompiler.compileAndExecute(javaFile, stdlibPath)

        assertIs<CompilationResult.Success>(compilationResult)
        assertIs<ExecutionResult.Success>(executionResult)

        val output = executionResult.stdout
        assertTrue(output.contains("50"))
    }
}
