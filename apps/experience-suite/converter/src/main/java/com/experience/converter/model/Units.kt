package com.experience.converter.model

import java.math.BigDecimal
import java.math.MathContext

/** Working precision for all conversions (34 significant digits). */
internal val MC: MathContext = MathContext.DECIMAL128

/** Pi to 50 digits, used by angle units. */
val PI: BigDecimal = BigDecimal("3.14159265358979323846264338327950288419716939937510")

/**
 * How a unit maps to its category's base unit.
 *
 * All factors are kept as exact rational numbers (numerator / denominator) so that
 * conversions such as km/h -> m/s (x * 1000 / 3600) never accumulate binary
 * floating-point noise; the single division happens last at 34-digit precision.
 */
sealed interface Scale {
    fun toBase(v: BigDecimal): BigDecimal
    fun fromBase(v: BigDecimal): BigDecimal

    /** base = v * num / den */
    data class Linear(val num: BigDecimal, val den: BigDecimal = BigDecimal.ONE) : Scale {
        override fun toBase(v: BigDecimal): BigDecimal = v.multiply(num, MC).divide(den, MC)
        override fun fromBase(v: BigDecimal): BigDecimal = v.multiply(den, MC).divide(num, MC)
    }

    /** base = (v + pre) * num / den — temperature scales. */
    data class Affine(val pre: BigDecimal, val num: BigDecimal, val den: BigDecimal) : Scale {
        override fun toBase(v: BigDecimal): BigDecimal = v.add(pre, MC).multiply(num, MC).divide(den, MC)
        override fun fromBase(v: BigDecimal): BigDecimal = v.multiply(den, MC).divide(num, MC).subtract(pre, MC)
    }

    /** base = k / v — inverse units such as L/100 km (base km/L). Undefined at 0. */
    data class Reciprocal(val k: BigDecimal) : Scale {
        override fun toBase(v: BigDecimal): BigDecimal = k.divide(v, MC)
        override fun fromBase(v: BigDecimal): BigDecimal = k.divide(v, MC)
    }
}

data class UnitDef(val id: String, val name: String, val symbol: String, val scale: Scale)

enum class Category(val title: String) {
    LENGTH("Length"), AREA("Area"), VOLUME("Volume"), MASS("Mass"), TEMPERATURE("Temperature"),
    SPEED("Speed"), TIME("Time"), DIGITAL("Digital storage"), ENERGY("Energy"), PRESSURE("Pressure"),
    ANGLE("Angle"), FUEL("Fuel economy"), FREQUENCY("Frequency"),
    /** Handled by [BaseConverter], not by [UnitConverter]. */
    NUMBER_BASE("Number base");

    val units: List<UnitDef> get() = UnitCatalog.units(this)
}

private fun d(s: String) = BigDecimal(s)
private fun lin(id: String, name: String, symbol: String, num: String, den: String = "1") =
    UnitDef(id, name, symbol, Scale.Linear(d(num), d(den)))

object UnitCatalog {
    private val ONE = BigDecimal.ONE

    private val length = listOf(
        lin("nm", "Nanometre", "nm", "1E-9"),
        lin("um", "Micrometre", "µm", "1E-6"),
        lin("mm", "Millimetre", "mm", "0.001"),
        lin("cm", "Centimetre", "cm", "0.01"),
        lin("m", "Metre", "m", "1"),
        lin("km", "Kilometre", "km", "1000"),
        lin("in", "Inch", "in", "0.0254"),
        lin("ft", "Foot", "ft", "0.3048"),
        lin("yd", "Yard", "yd", "0.9144"),
        lin("mi", "Mile", "mi", "1609.344"),
        lin("nmi", "Nautical mile", "nmi", "1852"),
        lin("au", "Astronomical unit", "au", "149597870700"),
        lin("ly", "Light-year", "ly", "9460730472580800"),
    )

    private val area = listOf(
        lin("mm2", "Square millimetre", "mm²", "1E-6"),
        lin("cm2", "Square centimetre", "cm²", "1E-4"),
        lin("m2", "Square metre", "m²", "1"),
        lin("ha", "Hectare", "ha", "10000"),
        lin("km2", "Square kilometre", "km²", "1000000"),
        lin("in2", "Square inch", "in²", "0.00064516"),
        lin("ft2", "Square foot", "ft²", "0.09290304"),
        lin("yd2", "Square yard", "yd²", "0.83612736"),
        lin("ac", "Acre", "ac", "4046.8564224"),
        lin("mi2", "Square mile", "mi²", "2589988.110336"),
    )

    // Base: litre.
    private val volume = listOf(
        lin("ml", "Millilitre", "mL", "0.001"),
        lin("cl", "Centilitre", "cL", "0.01"),
        lin("l", "Litre", "L", "1"),
        lin("cm3", "Cubic centimetre", "cm³", "0.001"),
        lin("m3", "Cubic metre", "m³", "1000"),
        lin("in3", "Cubic inch", "in³", "0.016387064"),
        lin("ft3", "Cubic foot", "ft³", "28.316846592"),
        lin("tsp_us", "Teaspoon (US)", "tsp", "0.00492892159375"),
        lin("tbsp_us", "Tablespoon (US)", "tbsp", "0.01478676478125"),
        lin("floz_us", "Fluid ounce (US)", "fl oz", "0.0295735295625"),
        lin("cup_us", "Cup (US)", "cup", "0.2365882365"),
        lin("pt_us", "Pint (US)", "pt", "0.473176473"),
        lin("qt_us", "Quart (US)", "qt", "0.946352946"),
        lin("gal_us", "Gallon (US)", "gal", "3.785411784"),
        lin("floz_uk", "Fluid ounce (UK)", "fl oz (UK)", "0.0284130625"),
        lin("pt_uk", "Pint (UK)", "pt (UK)", "0.56826125"),
        lin("gal_uk", "Gallon (UK)", "gal (UK)", "4.54609"),
    )

    // Base: kilogram.
    private val mass = listOf(
        lin("ug", "Microgram", "µg", "1E-9"),
        lin("mg", "Milligram", "mg", "1E-6"),
        lin("g", "Gram", "g", "0.001"),
        lin("kg", "Kilogram", "kg", "1"),
        lin("t", "Tonne", "t", "1000"),
        lin("ct", "Carat", "ct", "0.0002"),
        lin("oz", "Ounce", "oz", "0.028349523125"),
        lin("lb", "Pound", "lb", "0.45359237"),
        lin("st", "Stone", "st", "6.35029318"),
        lin("ton_us", "Short ton (US)", "ton (US)", "907.18474"),
        lin("ton_uk", "Long ton (UK)", "ton (UK)", "1016.0469088"),
    )

    // Base: kelvin. base = (v + pre) * num / den.
    private val temperature = listOf(
        UnitDef("c", "Celsius", "°C", Scale.Affine(d("273.15"), ONE, ONE)),
        UnitDef("f", "Fahrenheit", "°F", Scale.Affine(d("459.67"), d("5"), d("9"))),
        UnitDef("k", "Kelvin", "K", Scale.Affine(BigDecimal.ZERO, ONE, ONE)),
        UnitDef("r", "Rankine", "°R", Scale.Affine(BigDecimal.ZERO, d("5"), d("9"))),
        UnitDef("re", "Réaumur", "°Ré", Scale.Affine(d("218.52"), d("5"), d("4"))),
    )

    // Base: metre per second.
    private val speed = listOf(
        lin("mps", "Metre per second", "m/s", "1"),
        lin("kmh", "Kilometre per hour", "km/h", "1000", "3600"),
        lin("mph", "Mile per hour", "mph", "1609.344", "3600"),
        lin("fps", "Foot per second", "ft/s", "0.3048"),
        lin("kn", "Knot", "kn", "1852", "3600"),
        lin("c", "Speed of light", "c", "299792458"),
    )

    // Base: second. Month and year use the mean Gregorian year (365.2425 days).
    private val time = listOf(
        lin("ns", "Nanosecond", "ns", "1E-9"),
        lin("us", "Microsecond", "µs", "1E-6"),
        lin("ms", "Millisecond", "ms", "0.001"),
        lin("s", "Second", "s", "1"),
        lin("min", "Minute", "min", "60"),
        lin("h", "Hour", "h", "3600"),
        lin("d", "Day", "d", "86400"),
        lin("wk", "Week", "wk", "604800"),
        lin("mo", "Month (average)", "mo", "2629746"),
        lin("yr", "Year (365.2425 d)", "yr", "31556952"),
        lin("dec", "Decade", "dec", "315569520"),
    )

    // Base: byte. Decimal (SI) prefixes and binary (IEC) prefixes.
    private val digital = listOf(
        lin("bit", "Bit", "bit", "1", "8"),
        lin("B", "Byte", "B", "1"),
        lin("kbit", "Kilobit", "kbit", "125"),
        lin("Mbit", "Megabit", "Mbit", "125000"),
        lin("Gbit", "Gigabit", "Gbit", "125000000"),
        lin("kB", "Kilobyte", "kB", "1000"),
        lin("MB", "Megabyte", "MB", "1E6"),
        lin("GB", "Gigabyte", "GB", "1E9"),
        lin("TB", "Terabyte", "TB", "1E12"),
        lin("PB", "Petabyte", "PB", "1E15"),
        lin("KiB", "Kibibyte", "KiB", "1024"),
        lin("MiB", "Mebibyte", "MiB", "1048576"),
        lin("GiB", "Gibibyte", "GiB", "1073741824"),
        lin("TiB", "Tebibyte", "TiB", "1099511627776"),
        lin("PiB", "Pebibyte", "PiB", "1125899906842624"),
    )

    // Base: joule.
    private val energy = listOf(
        lin("J", "Joule", "J", "1"),
        lin("kJ", "Kilojoule", "kJ", "1000"),
        lin("MJ", "Megajoule", "MJ", "1E6"),
        lin("cal", "Calorie (thermochemical)", "cal", "4.184"),
        lin("kcal", "Kilocalorie", "kcal", "4184"),
        lin("Wh", "Watt-hour", "Wh", "3600"),
        lin("kWh", "Kilowatt-hour", "kWh", "3600000"),
        lin("eV", "Electronvolt", "eV", "1.602176634E-19"),
        lin("BTU", "British thermal unit (IT)", "BTU", "1055.05585262"),
        lin("ftlbf", "Foot-pound force", "ft·lbf", "1.3558179483314004"),
        lin("erg", "Erg", "erg", "1E-7"),
    )

    // Base: pascal. psi = lbf / in² = 4.4482216152605 N / 0.00064516 m².
    private val pressure = listOf(
        lin("Pa", "Pascal", "Pa", "1"),
        lin("hPa", "Hectopascal", "hPa", "100"),
        lin("kPa", "Kilopascal", "kPa", "1000"),
        lin("MPa", "Megapascal", "MPa", "1000000"),
        lin("bar", "Bar", "bar", "100000"),
        lin("mbar", "Millibar", "mbar", "100"),
        lin("atm", "Standard atmosphere", "atm", "101325"),
        lin("psi", "Pound per square inch", "psi", "4.4482216152605", "0.00064516"),
        lin("torr", "Torr", "Torr", "101325", "760"),
        lin("mmHg", "Millimetre of mercury", "mmHg", "133.322387415"),
        lin("inHg", "Inch of mercury", "inHg", "3386.389"),
    )

    // Base: degree.
    private val angle = listOf(
        lin("deg", "Degree", "°", "1"),
        UnitDef("rad", "Radian", "rad", Scale.Linear(d("180"), PI)),
        lin("grad", "Gradian", "gon", "0.9"),
        lin("arcmin", "Arcminute", "′", "1", "60"),
        lin("arcsec", "Arcsecond", "″", "1", "3600"),
        lin("turn", "Turn", "tr", "360"),
        lin("mil", "Mil (NATO)", "mil", "360", "6400"),
    )

    // Base: kilometre per litre. L/100 km is the inverse: km/L = 100 / x.
    private val fuel = listOf(
        lin("kmpl", "Kilometre per litre", "km/L", "1"),
        UnitDef("l100km", "Litre per 100 km", "L/100 km", Scale.Reciprocal(d("100"))),
        lin("mpg_us", "Mile per gallon (US)", "mpg (US)", "1.609344", "3.785411784"),
        lin("mpg_uk", "Mile per gallon (UK)", "mpg (UK)", "1.609344", "4.54609"),
        lin("mpl", "Mile per litre", "mi/L", "1.609344"),
    )

    // Base: hertz.
    private val frequency = listOf(
        lin("Hz", "Hertz", "Hz", "1"),
        lin("kHz", "Kilohertz", "kHz", "1000"),
        lin("MHz", "Megahertz", "MHz", "1E6"),
        lin("GHz", "Gigahertz", "GHz", "1E9"),
        lin("THz", "Terahertz", "THz", "1E12"),
        lin("rpm", "Revolution per minute", "rpm", "1", "60"),
        UnitDef("rads", "Radian per second", "rad/s", Scale.Linear(ONE, PI.multiply(d("2")))),
    )

    fun units(category: Category): List<UnitDef> = when (category) {
        Category.LENGTH -> length
        Category.AREA -> area
        Category.VOLUME -> volume
        Category.MASS -> mass
        Category.TEMPERATURE -> temperature
        Category.SPEED -> speed
        Category.TIME -> time
        Category.DIGITAL -> digital
        Category.ENERGY -> energy
        Category.PRESSURE -> pressure
        Category.ANGLE -> angle
        Category.FUEL -> fuel
        Category.FREQUENCY -> frequency
        Category.NUMBER_BASE -> emptyList()
    }

    fun find(category: Category, id: String): UnitDef? = units(category).firstOrNull { it.id == id }

    /** Sensible starting pair for each category. */
    fun defaults(category: Category): Pair<String, String> = when (category) {
        Category.LENGTH -> "m" to "ft"
        Category.AREA -> "m2" to "ft2"
        Category.VOLUME -> "l" to "gal_us"
        Category.MASS -> "kg" to "lb"
        Category.TEMPERATURE -> "c" to "f"
        Category.SPEED -> "kmh" to "mph"
        Category.TIME -> "h" to "min"
        Category.DIGITAL -> "GB" to "GiB"
        Category.ENERGY -> "kcal" to "kJ"
        Category.PRESSURE -> "bar" to "psi"
        Category.ANGLE -> "deg" to "rad"
        Category.FUEL -> "l100km" to "mpg_us"
        Category.FREQUENCY -> "MHz" to "Hz"
        Category.NUMBER_BASE -> "10" to "16"
    }
}
