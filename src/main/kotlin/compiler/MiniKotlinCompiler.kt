package org.example.compiler

import MiniKotlinBaseVisitor
import MiniKotlinParser

class MiniKotlinCompiler : MiniKotlinBaseVisitor<String>() {
    private var temporaryVars: MutableList<String> = mutableListOf()
    private var temporaryVarDeclIndex = 0
    private var temporaryVarUsageIndex = 0
    private var temporaryVarName = "arg"
    private var continuationVarName = "__continuation"
    private val parser = CpsParser()

    fun getNewContinuationArg(): String {
        var arg = "arg" + temporaryVarDeclIndex++
        while (arg in parser.symbols) {
            arg = "arg" + temporaryVarDeclIndex++
        }

        temporaryVars.add(arg)
        return arg
    }

    private fun compileFunctionParams(type: String, params: MiniKotlinParser.ParameterListContext?): String {
        var continuationSymbol = "__continuation"
        var counter = 0
        while (continuationSymbol in parser.symbols) {
            continuationSymbol = "__continuation" + counter++
        }

        if (params == null) return "Continuation<$type> $continuationSymbol"

        var paramString = ""

        for (param in params.parameter()) {
            val paramType = parseType(param.type())
            val paramName = param.IDENTIFIER()
            paramString += "$paramType $paramName, "
        }

        paramString += "Continuation<$type> $continuationSymbol"

        return paramString
    }

    private fun compileExpression(expr: MiniKotlinParser.ExpressionContext): String {
        return when (expr) {
            is MiniKotlinParser.AndExprContext -> compileAndExpr(expr)
            is MiniKotlinParser.FunctionCallExprContext -> compileFunctionCallExpression()
            is MiniKotlinParser.MulDivExprContext -> compileMulDivExpr(expr)
            is MiniKotlinParser.EqualityExprContext -> compileEqualityExpr(expr)
            is MiniKotlinParser.ComparisonExprContext -> compileComparisonExpr(expr)
            is MiniKotlinParser.PrimaryExprContext -> compilePrimaryExpr(expr)
            is MiniKotlinParser.NotExprContext -> compileNotExpr(expr)
            is MiniKotlinParser.AddSubExprContext -> compileAddSubExpr(expr)
            is MiniKotlinParser.OrExprContext -> compileOrExpr(expr)
            else -> throw Exception("invalid expression type")
        }
    }

    private fun compileAndExpr(expr: MiniKotlinParser.AndExprContext): String {
        val left = compileExpression(expr.expression(0))
        val right = compileExpression(expr.expression(1))
        return "(" + left + "&&" + right + ")"
    }

    private fun compileFunctionCallExpression(): String {
        return temporaryVars[temporaryVarUsageIndex++]
    }

    private fun compileMulDivExpr(expr: MiniKotlinParser.MulDivExprContext): String {
        val left = compileExpression(expr.expression(0))
        val right = compileExpression(expr.expression(1))
        val op = expr.MULT()?.text ?: expr.DIV()?.text ?: expr.MOD()?.text
        return "(" + left + op + right + ")"
    }

    private fun compileEqualityExpr(expr: MiniKotlinParser.EqualityExprContext): String {
        val left = compileExpression(expr.expression(0))
        val right = compileExpression(expr.expression(1))
        val op = expr.EQ()?.text ?: expr.NEQ()?.text
        return "(" + left + op + right + ")"
    }

    private fun compileComparisonExpr(expr: MiniKotlinParser.ComparisonExprContext): String {
        val left = compileExpression(expr.expression(0))
        val right = compileExpression(expr.expression(1))
        val op = expr.LT()?.text ?: expr.GT()?.text ?: expr.LE()?.text ?: expr.GE()?.text
        return "(" + left + op + right + ")"
    }

    private fun compilePrimaryExpr(expr: MiniKotlinParser.PrimaryExprContext): String {
        return expr.text
    }

    private fun compileNotExpr(expr: MiniKotlinParser.NotExprContext): String {
        return "!" + compileExpression(expr.expression())
    }

    private fun compileAddSubExpr(expr: MiniKotlinParser.AddSubExprContext): String {
        val left = compileExpression(expr.expression(0))
        val right = compileExpression(expr.expression(1))
        val op = expr.PLUS()?.text ?: expr.MINUS()?.text
        return "(" + left + op + right + ")"
    }

    private fun compileOrExpr(expr: MiniKotlinParser.OrExprContext): String {
        val left = compileExpression(expr.expression(0))
        val right = compileExpression(expr.expression(1))
        return "(" + left + "||" + right + ")"
    }

    private fun compileIfStatement(statement: Statement.IfStatement): String {
        val expr = compileExpression(statement.expr)
        val ifBlock = compileBlock(statement.statements1)

        if (statement.statements2.isEmpty()) {
            return "if ($expr) {\n$ifBlock}"
        }

        val elseBlock = compileBlock(statement.statements2)
        return "if ($expr) {\n$ifBlock} else {\n$elseBlock}"
    }

    private fun compileWhileStatement(statement: Statement.WhileStatement): String {
        val expr = compileExpression(statement.expr)
        val block = compileBlock(statement.statements)
        return "while ($expr) {\n$block}"
    }

    private fun CompileVariableAssigment(statement: Statement.VariableAssignment): String {
        val name = statement.name
        val expr = compileExpression(statement.expr)
        return "$name = $expr;"
    }

    private fun compileReturnStatement(statement: Statement.ReturnStatement): String {
        val expr = compileExpression(statement.expr)
        return """
            __continuation.accept($expr);
            return;
        """.trimIndent()
    }

    private fun compileVariableDeclaration(decl: Statement.VariableDeclaration): String {
        val type = decl.type
        val name = decl.name
        val expr = compileExpression(decl.expr)
        return "$type $name = $expr;"
    }

    private fun compileArgs(args: List<MiniKotlinParser.ExpressionContext>): String {
        return args.map { compileExpression(it) }.joinToString(", ")
    }

    private fun compileExpressionStatement(statement: Statement.Expression): String {
        compileExpression(statement.expr) // parse to process potential function calls
        return "" // return nothing, would be an invalid expression
    }

    private fun compileCpsFunctionCall(statement: Statement.CpsFunctionCall): String {
        val name: String
        if (statement.name.equals("println")) {
            name = "Prelude.println"
        } else {
            name = statement.name
        }

        val arg = getNewContinuationArg()
        val args = compileArgs(statement.args)
        val block = compileBlock(statement.statements)

        if (block.trim().equals("")) {
            return "$name($args, ($arg) -> {});"
        }

        return "$name($args, ($arg) -> {\n$block});"
    }

    private fun compileBlock(statements: List<Statement>): String {
        var output = ""

        for (statement in statements) {
            val statementString =
                    when (statement) {
                        is Statement.VariableDeclaration -> compileVariableDeclaration(statement)
                        is Statement.IfStatement -> compileIfStatement(statement)
                        is Statement.WhileStatement -> compileWhileStatement(statement)
                        is Statement.VariableAssignment -> CompileVariableAssigment(statement)
                        is Statement.ReturnStatement -> compileReturnStatement(statement)
                        is Statement.Expression -> compileExpressionStatement(statement)
                        is Statement.CpsFunctionCall -> compileCpsFunctionCall(statement)
                    }

            if (!output.equals("") && !statementString.equals("")) output += "\n"
            output += statementString
        }

        return output.prependIndent("  ") + "\n"
    }

    private fun compileFunctionDeclaration(funcName: String): String {
        val func = parser.functions[funcName] ?: return ""

        val params: String
        if (funcName.equals("main")) {
            params = "String[] args"
        } else if (func.type == "void") {
            params = compileFunctionParams("Void", func.params)
        } else {
            params = compileFunctionParams(func.type, func.params)
        }

        val block = compileBlock(func.statements)

        return "public static void $funcName($params) {\n$block}".prependIndent("  ") + "\n"
    }

    fun compile(
            program: MiniKotlinParser.ProgramContext,
            className: String = "MiniProgram"
    ): String {
        parser.parseProgram(program)

        var output = "public class $className {\n"

        for (funcName in parser.functions.keys) {
            output += compileFunctionDeclaration(funcName)
        }

        output += "}"

        return output
    }
}
