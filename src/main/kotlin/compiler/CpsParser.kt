package org.example.compiler

internal fun parseType(type: MiniKotlinParser.TypeContext): String {
    return when (type.text) {
        "Int" -> "Integer"
        "String" -> "String"
        "Boolean" -> "Boolean"
        "Unit" -> "void"
        else -> throw Exception("Syntax error: invalid type \"${type.text}\"")
    }
}

data class Function(
        val type: String,
        val params: MiniKotlinParser.ParameterListContext?,
        val statements: List<Statement>
)

sealed class Statement {
    data class IfStatement(
            val expr: MiniKotlinParser.ExpressionContext,
            val statements1: List<Statement>,
            val statements2: List<Statement>
    ) : Statement()
    data class VariableDeclaration(
            val type: String,
            val name: String,
            val expr: MiniKotlinParser.ExpressionContext
    ) : Statement()
    data class WhileStatement(
            val expr: MiniKotlinParser.ExpressionContext,
            val statements: List<Statement>
    ) : Statement()
    data class VariableAssignment(val name: String, val expr: MiniKotlinParser.ExpressionContext) :
            Statement()
    data class ReturnStatement(val expr: MiniKotlinParser.ExpressionContext) : Statement()
    data class Expression(val expr: MiniKotlinParser.ExpressionContext) : Statement()
    data class CpsFunctionCall(
            val name: String,
            val args: List<MiniKotlinParser.ExpressionContext>,
            val statements: List<Statement>
    ) : Statement()
}

class CpsParser {
    val functions: HashMap<String, Function> = HashMap()
    val symbols: MutableList<String> = mutableListOf()
    private val blockStack: MutableList<MutableList<Statement>> = mutableListOf()

    fun parseProgram(program: MiniKotlinParser.ProgramContext) {
        for (func in program.functionDeclaration()) {
            val name = func.IDENTIFIER().text
            val type = parseType(func.type())
            val params = func.parameterList()
            val statements = parseBlock(func.block())

            functions[name] = Function(type, params, statements)
        }
    }

    private fun parseBlock(block: MiniKotlinParser.BlockContext): List<Statement> {
        val statements = mutableListOf<Statement>()
        blockStack.add(statements)

        for (statement in block.statement()) {
            val newStatement =
                    when {
                        statement.variableDeclaration() != null -> {
                            val decl = statement.variableDeclaration()
                            parseExpression(decl.expression())
                            Statement.VariableDeclaration(
                                    parseType(decl.type()),
                                    decl.IDENTIFIER().text,
                                    decl.expression()
                            )
                        }
                        statement.ifStatement() != null -> {
                            val ifStatement = statement.ifStatement()
                            parseExpression(ifStatement.expression())
                            val stackSize = blockStack.size

                            val block0 = parseBlock(ifStatement.block(0))
                            while (blockStack.size > stackSize) {
                                blockStack.remove(blockStack.last())
                            }

                            if (ifStatement.ELSE() == null) {
                                Statement.IfStatement(ifStatement.expression(), block0, emptyList())
                            } else {
                                val block1 = parseBlock(ifStatement.block(1))
                                while (blockStack.size > stackSize) {
                                    blockStack.remove(blockStack.last())
                                }

                                Statement.IfStatement(ifStatement.expression(), block0, block1)
                            }
                        }
                        statement.whileStatement() != null -> {
                            val whileStatement = statement.whileStatement()
                            parseExpression(whileStatement.expression())
                            val stackSize = blockStack.size

                            val block0 = parseBlock(whileStatement.block())
                            while (blockStack.size > stackSize) {
                                blockStack.remove(blockStack.last())
                            }

                            Statement.WhileStatement(
                                    whileStatement.expression(),
                                    block0
                            )
                        }
                        statement.variableAssignment() != null -> {
                            val assignment = statement.variableAssignment()
                            parseExpression(assignment.expression())
                            Statement.VariableAssignment(
                                    assignment.IDENTIFIER().text,
                                    assignment.expression()
                            )
                        }
                        statement.returnStatement() != null -> {
                            val returnStatement = statement.returnStatement()
                            parseExpression(returnStatement.expression())
                            Statement.ReturnStatement(returnStatement.expression())
                        }
                        statement.expression() != null -> {
                            parseExpression(statement.expression())
                            Statement.Expression(statement.expression())
                        }
                        else -> throw Exception("Invalid statement type")
                    }

            blockStack.last().add(newStatement)
        }

        blockStack.remove(statements)
        return statements
    }

    private fun parseExpression(expr: MiniKotlinParser.ExpressionContext) {
        when (expr) {
            is MiniKotlinParser.AndExprContext -> parseAndExpr(expr)
            is MiniKotlinParser.FunctionCallExprContext -> parseFunctionCall(expr)
            is MiniKotlinParser.MulDivExprContext -> parseMulDivExpr(expr)
            is MiniKotlinParser.EqualityExprContext -> parseEqualityExpr(expr)
            is MiniKotlinParser.ComparisonExprContext -> parseComparisonExpr(expr)
            is MiniKotlinParser.PrimaryExprContext -> parsePrimaryExpr(expr)
            is MiniKotlinParser.NotExprContext -> parseNotExpr(expr)
            is MiniKotlinParser.AddSubExprContext -> parseAddSubExpr(expr)
            is MiniKotlinParser.OrExprContext -> parseOrExpr(expr)
        }
    }

    private fun parseAndExpr(expr: MiniKotlinParser.AndExprContext) {
        for (inner in expr.expression()) {
            parseExpression(inner)
        }
    }

    private fun parseMulDivExpr(expr: MiniKotlinParser.MulDivExprContext) {
        for (inner in expr.expression()) {
            parseExpression(inner)
        }
    }

    private fun parseEqualityExpr(expr: MiniKotlinParser.EqualityExprContext) {
        for (inner in expr.expression()) {
            parseExpression(inner)
        }
    }

    private fun parseComparisonExpr(expr: MiniKotlinParser.ComparisonExprContext) {
        for (inner in expr.expression()) {
            parseExpression(inner)
        }
    }

    private fun parsePrimaryExpr(expr: MiniKotlinParser.PrimaryExprContext) {
        if (expr.primary() is MiniKotlinParser.IdentifierExprContext) {
            symbols.add(expr.text)
        }
    }

    private fun parseNotExpr(expr: MiniKotlinParser.NotExprContext) {
        parseExpression(expr.expression())
    }

    private fun parseAddSubExpr(expr: MiniKotlinParser.AddSubExprContext) {
        for (inner in expr.expression()) {
            parseExpression(inner)
        }
    }

    private fun parseOrExpr(expr: MiniKotlinParser.OrExprContext) {
        for (inner in expr.expression()) {
            parseExpression(inner)
        }
    }

    private fun parseFunctionCall(expr: MiniKotlinParser.FunctionCallExprContext) {
        val name = expr.IDENTIFIER().text
        val args = expr.argumentList().expression()
        val statements = mutableListOf<Statement>()

        for (argExpr in expr.argumentList().expression()) {
            parseExpression(argExpr)
        }

        blockStack.last().add(Statement.CpsFunctionCall(name, args, statements))
        blockStack.add(statements)
    }
}
