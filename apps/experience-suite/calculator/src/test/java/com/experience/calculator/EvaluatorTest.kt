package com.experience.calculator

import com.experience.calculator.engine.AngleMode
import com.experience.calculator.engine.CalcError
import com.experience.calculator.engine.CalcResult
import com.experience.calculator.engine.Evaluator
import com.experience.calculator.engine.NumberFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class EvaluatorTest {
    private fun eval(s: String, mode: AngleMode = AngleMode.DEG): String =
        when (val r = Evaluator.evaluate(s, mode)) {
            is CalcResult.Value -> NumberFormatter.format(r.value)
            is CalcResult.Failure -> "ERR:" + r.error
        }

    private fun err(s: String, e: CalcError, mode: AngleMode = AngleMode.DEG) = assertEquals("ERR:$e", eval(s, mode))

    @Test fun precedence() {
        assertEquals("14", eval("2+3×4"))
        assertEquals("20", eval("(2+3)×4"))
        assertEquals("1", eval("8÷4÷2"))
        assertEquals("5", eval("10−3−2"))
        assertEquals("7", eval("1+2*3"))
        assertEquals("3.5", eval("7/2"))
    }

    @Test fun powerIsRightAssociative() {
        assertEquals("512", eval("2^3^2"))
        assertEquals("64", eval("(2^3)^2"))
        assertEquals("0.5", eval("2^−1"))
        assertEquals("18", eval("2×3^2"))
    }

    @Test fun unaryMinus() {
        assertEquals("-4", eval("−2^2"))
        assertEquals("4", eval("(−2)^2"))
        assertEquals("-6", eval("2×−3"))
        assertEquals("5", eval("−−5"))
        assertEquals("-6", eval("−3!"))
    }

    @Test fun implicitMultiplication() {
        assertEquals("12", eval("3(4)"))
        assertEquals("6", eval("(2)(3)"))
        assertEquals("6.28318530717959", eval("2π"))
        assertEquals("6", eval("2√9"))
        assertEquals("1", eval("2sin(30)"))
    }

    @Test fun autoCloseParentheses() {
        assertEquals("8", eval("(2+3×(2"))
        assertEquals("20", eval("((2+3)×(2+2"))
        assertEquals("0.5", eval("sin(30"))
    }

    @Test fun noFloatingNoise() {
        assertEquals("0.3", eval("0.1+0.2"))
        assertEquals("1", eval("1÷3×3"))
        assertEquals("0.333333333333333", eval("1÷3"))
        assertEquals("1.21", eval("1.1^2"))
        assertEquals("3", eval("log(1000)"))
        assertEquals("-2", eval("log(0.01)"))
        assertEquals("1", eval("ln(e)"))
    }

    @Test fun percentAndFactorial() {
        assertEquals("0.5", eval("50%"))
        assertEquals("25", eval("50%×50"))
        assertEquals("120", eval("5!"))
        assertEquals("1", eval("0!"))
        assertEquals("3628800", eval("10!"))
        assertEquals("720", eval("3!!"))
        assertEquals("9.33262154439442E157", eval("100!"))
    }

    @Test fun factorialLimits() {
        err("2.5!", CalcError.DOMAIN)
        err("(−1)!", CalcError.DOMAIN)
        err("3001!", CalcError.OVERFLOW)
        assertEquals(true, eval("3000!").startsWith("4.14935960343785E9130"))
    }

    @Test fun trigAtExactAngles() {
        assertEquals("0", eval("sin(180)"))
        assertEquals("0", eval("sin(360)"))
        assertEquals("-1", eval("sin(270)"))
        assertEquals("0.5", eval("sin(30)"))
        assertEquals("0.5", eval("cos(60)"))
        assertEquals("0", eval("cos(90)"))
        assertEquals("-1", eval("cos(180)"))
        assertEquals("1", eval("tan(45)"))
        assertEquals("0", eval("tan(180)"))
        assertEquals("-0.5", eval("sin(−30)"))
        assertEquals("0.5", eval("sin(390)"))
        err("tan(90)", CalcError.DOMAIN)
        err("tan(−270)", CalcError.DOMAIN)
        assertEquals("0.707106781186548", eval("sin(45)"))
    }

    @Test fun trigInRadians() {
        assertEquals("0", eval("sin(π)", AngleMode.RAD))
        assertEquals("-1", eval("cos(π)", AngleMode.RAD))
        assertEquals("1", eval("sin(π÷2)", AngleMode.RAD))
        err("tan(π÷2)", CalcError.DOMAIN, AngleMode.RAD)
        assertEquals("0.841470984807897", eval("sin(1)", AngleMode.RAD))
    }

    @Test fun inverseTrig() {
        assertEquals("30", eval("sin⁻¹(0.5)"))
        assertEquals("90", eval("asin(1)"))
        assertEquals("60", eval("cos⁻¹(0.5)"))
        assertEquals("45", eval("tan⁻¹(1)"))
        assertEquals("3.14159265358979", eval("acos(−1)", AngleMode.RAD))
        err("sin⁻¹(2)", CalcError.DOMAIN)
    }

    @Test fun rootsAndPowers() {
        assertEquals("3", eval("√9"))
        assertEquals("1.4142135623731", eval("√2"))
        assertEquals("4", eval("√4^2"))
        assertEquals("-2", eval("(−8)^(1÷3)"))
        assertEquals("1", eval("5^0"))
        assertEquals("1024", eval("2^10"))
        assertEquals("1.4142135623731", eval("2^0.5"))
        assertEquals("1E-20", eval("10^−20"))
        err("√−1", CalcError.DOMAIN)
        err("(−8)^0.5", CalcError.DOMAIN)
        err("10^99999", CalcError.OVERFLOW)
        err("0^−1", CalcError.DIV_ZERO)
    }

    @Test fun errors() {
        err("1÷0", CalcError.DIV_ZERO)
        err("5÷(3−3)", CalcError.DIV_ZERO)
        err("ln(0)", CalcError.DOMAIN)
        err("log(−1)", CalcError.DOMAIN)
        err("2+", CalcError.SYNTAX)
        err("×3", CalcError.SYNTAX)
        err("1..2", CalcError.SYNTAX)
        err("2)", CalcError.SYNTAX)
        err("", CalcError.SYNTAX)
        err("abc", CalcError.SYNTAX)
    }

    @Test fun scientificNotationInput() {
        assertEquals("1500", eval("1.5E3"))
        assertEquals("0.002", eval("2E−3"))
        assertEquals("3E20", eval("1.5E20×2"))
    }

    @Test fun constants() {
        assertEquals("3.14159265358979", eval("π"))
        assertEquals("2.71828182845905", eval("e"))
        assertEquals("1", eval("cos(0)"))
    }
}
