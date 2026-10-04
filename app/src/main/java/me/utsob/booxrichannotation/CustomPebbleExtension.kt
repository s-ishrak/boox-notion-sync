package me.utsob.booxrichannotation

import io.pebbletemplates.pebble.extension.AbstractExtension
import io.pebbletemplates.pebble.extension.Function
import io.pebbletemplates.pebble.template.EvaluationContext
import io.pebbletemplates.pebble.template.PebbleTemplate
import java.text.DecimalFormat

class CustomPebbleExtension : AbstractExtension() {
    override fun getFunctions(): Map<String, Function> {
        return mapOf("percentage" to PercentageFunction())
    }
}

class PercentageFunction : Function {
    override fun execute(
        args: MutableMap<String, Any>?,
        self: PebbleTemplate?,
        context: EvaluationContext?,
        lineNumber: Int
    ): Any? {
        if (args == null || args.isEmpty()) return "0"
        
        // Arguments are provided with keys matching the argument names
        val amount = when (val amountArg = args["amount"]) {
            is Number -> amountArg.toDouble()
            is String -> amountArg.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
        
        val whole = when (val wholeArg = args["whole"]) {
            is Number -> wholeArg.toDouble()
            is String -> wholeArg.toDoubleOrNull() ?: 1.0
            else -> 1.0
        }
        
        // Prevent division by zero
        if (whole == 0.0) return "0"
        
        val decimalPlaces = when (val decimalsArg = args["decimalPlaces"]) {
            is Number -> decimalsArg.toInt()
            is String -> decimalsArg.toIntOrNull() ?: 2
            else -> 2
        }
        
        // Calculate percentage - force double division
        val percentage = (amount * 100.0) / whole
        
        // Format with specified decimal places
        val pattern = "0." + "0".repeat(decimalPlaces.coerceIn(0, 10))
        val formatter = DecimalFormat(pattern)
        
        return formatter.format(percentage)
    }
    
    override fun getArgumentNames(): List<String> {
        return listOf("amount", "whole", "decimalPlaces")
    }
}
