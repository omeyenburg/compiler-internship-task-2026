package org.example.compiler

import MiniKotlinBaseVisitor
import MiniKotlinParser

class MiniKotlinCompiler : MiniKotlinBaseVisitor<String>() {

    private fun parse_type(type: MiniKotlinParser.TypeContext): String {
        return when (type.text) {
            "Int" -> "Integer"
            "String" -> "String"
            "Boolean" -> "Boolean"
            "Unit" -> "void"
            else -> throw Exception("Syntax error: invalid type \"${type.text}\"")
        }
    }

    private fun parse_function_params(params: MiniKotlinParser.ParameterListContext?): String {
        if (params == null) return "Continuation<Integer> __continuation"

        var param_string = ""

        for (param in params.parameter()) {
            val param_type = parse_type(param.type())
            val param_name = param.IDENTIFIER()
            param_string += "$param_type $param_name, "
        }

        param_string += "Continuation<Integer> __continuation" // TODO: check usage

        return param_string
    }

    private fun parse_expression(expr: MiniKotlinParser.ExpressionContext): String {
        return "..."
    }

    private fun parse_if_statement(expr: MiniKotlinParser.IfStatementContext): String {
        return "..."
    }

    private fun parse_while_statement(expr: MiniKotlinParser.WhileStatementContext): String {
        return "..."
    }

    private fun parse_variable_assigment(expr: MiniKotlinParser.VariableAssignmentContext): String {
        return "..."
    }

    private fun parse_return_statement(expr: MiniKotlinParser.ReturnStatementContext): String {
        return "..."
    }

    private fun parse_variable_declaration(decl: MiniKotlinParser.VariableDeclarationContext): String {
        val type = parse_type(decl.type())
        val name = decl.IDENTIFIER()
        val expr = parse_expression(decl.expression())
        return "$type $name = $expr"
    }

    private fun parse_block(block: MiniKotlinParser.BlockContext): String {
        var output = ""

        for (statement in block.statement()) {
            output += if (statement.variableDeclaration() != null) {
                parse_variable_declaration(statement.variableDeclaration())
            } else if (statement.ifStatement() != null) {
                parse_if_statement(statement.ifStatement())
            } else if (statement.whileStatement() != null) {
                parse_while_statement(statement.whileStatement())
            } else if (statement.variableAssignment() != null) {
                parse_variable_assigment(statement.variableAssignment())
            } else if (statement.returnStatement() != null) {
                parse_return_statement(statement.returnStatement())
            } else if (statement.expression() != null) {
                parse_expression(statement.expression())
            } else {
                throw Exception("Syntax error: invalid statement \"${statement.text}\"")
            }

            output += "\n"
        }

        return output
    }

    private fun compile_function_def(func: MiniKotlinParser.FunctionDeclarationContext): String {
        val name = func.IDENTIFIER()
        val type = parse_type(func.type())

        val params: String
        if (name.equals("main")) {
            params = "String[] args"
        } else {
            params = parse_function_params(func.parameterList())
        }

        val block = parse_block(func.block())

        return "public static $type $name($params) {\n$block}"
    }

    fun compile(program: MiniKotlinParser.ProgramContext, className: String = "MiniProgram"): String {
        var output = "public class $className {\n"

        for (func in program.functionDeclaration()) {
            output += compile_function_def(func).prependIndent("    ") + "\n"
        }

        output += "}"

        // return """
        //     public class $className {
        //         public static void main(String[] args) {
        //           return;
        //         }
        //     }
        // """.trimIndent()

        return output
    }
}
