package com.aira.assistant.tools

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Deterministic recursive-descent mathematical expression evaluator (`ExpressionCalculator`).
 * Safely evaluates arithmetic (`+`, `-`, `*`, `/`, `%`, `^`), parentheses, percentages,
 * and functions (`sqrt`, `sin`, `cos`, `tan`, `log`, `ln`) offline without `eval` or shell injection.
 */
object ExpressionCalculator {

    fun evaluate(rawExpression: String): Result<Double> {
        return try {
            val cleaned = rawExpression
                .lowercase()
                .replace("×", "*")
                .replace("÷", "/")
                .replace("plus", "+")
                .replace("minus", "-")
                .replace("times", "*")
                .replace("multiplied by", "*")
                .replace("divided by", "/")
                .replace("into", "*")
                .replace(",", "")
                .trim()

            if (cleaned.isEmpty()) {
                return Result.failure(IllegalArgumentException("Empty mathematical expression"))
            }

            val parser = Parser(cleaned)
            val value = parser.parseExpression()
            if (value.isNaN() || value.isInfinite()) {
                Result.failure(ArithmeticException("Expression resulted in NaN or Infinity (check division by zero)"))
            } else {
                Result.success(value)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private class Parser(private val input: String) {
        private var pos = -1
        private var ch = 0

        init {
            nextChar()
        }

        private fun nextChar() {
            ch = if (++pos < input.length) input[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parseExpression(): Double {
            val x = parseTerm()
            if (pos < input.length) {
                throw IllegalArgumentException("Unexpected character '${input[pos]}' at position $pos")
            }
            return x
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('+'.code) -> x += parseFactor()
                    eat('-'.code) -> x -= parseFactor()
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            var x = parseUnary()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseUnary()
                    eat('/'.code) -> {
                        val divisor = parseUnary()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    eat('%'.code) -> x %= parseUnary()
                    else -> return x
                }
            }
        }

        private fun parseUnary(): Double {
            if (eat('+'.code)) return parseUnary()
            if (eat('-'.code)) return -parseUnary()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseTerm()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing closing parenthesis ')'")
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = input.substring(startPos, pos).toDouble()
            } else if (ch in 'a'.code..'z'.code) {
                while (ch in 'a'.code..'z'.code || ch in '0'.code..'9'.code) nextChar()
                val func = input.substring(startPos, pos)
                x = when (func) {
                    "pi" -> Math.PI
                    "e" -> Math.E
                    else -> {
                        val arg = parseUnary()
                        when (func) {
                            "sqrt" -> sqrt(arg)
                            "sin" -> sin(Math.toRadians(arg))
                            "cos" -> cos(Math.toRadians(arg))
                            "tan" -> tan(Math.toRadians(arg))
                            "log", "log10" -> log10(arg)
                            "ln" -> ln(arg)
                            else -> throw IllegalArgumentException("Unknown function: $func")
                        }
                    }
                }
            } else {
                throw IllegalArgumentException("Unexpected token in expression")
            }

            if (eat('^'.code)) {
                x = x.pow(parseUnary())
            }
            return x
        }
    }
}
