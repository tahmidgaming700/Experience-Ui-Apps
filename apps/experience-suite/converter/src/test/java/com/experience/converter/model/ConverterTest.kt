package com.experience.converter.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class ConverterTest {

    private fun conv(c: Category, v: String, from: String, to: String, digits: Int = 12): String {
        val f = UnitCatalog.find(c, from) ?: error("no $from")
        val t = UnitCatalog.find(c, to) ?: error("no $to")
        return UnitConverter.convert(BigDecimal(v), f, t)?.let { NumberFormat.format(it, digits) } ?: "null"
    }

    @Test fun everyCategoryHasUniqueIdsAndValidDefaults() {
        Category.entries.filter { it != Category.NUMBER_BASE }.forEach { c ->
            val ids = c.units.map { it.id }
            assertEquals("duplicate ids in $c", ids.size, ids.toSet().size)
            val (f, t) = UnitCatalog.defaults(c)
            assertTrue("$c default from", UnitCatalog.find(c, f) != null)
            assertTrue("$c default to", UnitCatalog.find(c, t) != null)
        }
    }

    @Test fun everyUnitRoundTripsThroughEveryOther() {
        val v = BigDecimal("123.456")
        Category.entries.filter { it != Category.NUMBER_BASE }.forEach { c ->
            for (a in c.units) for (b in c.units) {
                val there = UnitConverter.convert(v, a, b)!!
                val back = UnitConverter.convert(there, b, a)!!
                assertEquals("$c ${a.id}->${b.id}", "123.456", NumberFormat.format(back))
            }
        }
    }

    @Test fun length() {
        assertEquals("1609.344", conv(Category.LENGTH, "1", "mi", "m"))
        assertEquals("12", conv(Category.LENGTH, "1", "ft", "in"))
        assertEquals("2.54", conv(Category.LENGTH, "1", "in", "cm"))
        assertEquals("1.852", conv(Category.LENGTH, "1", "nmi", "km"))
        assertEquals("0.621371192237", conv(Category.LENGTH, "1", "km", "mi"))
        assertEquals("9.4607304725808E+15", conv(Category.LENGTH, "1", "ly", "m", 14))
    }

    @Test fun area() {
        assertEquals("10000", conv(Category.AREA, "1", "ha", "m2"))
        assertEquals("144", conv(Category.AREA, "1", "ft2", "in2"))
        assertEquals("640", conv(Category.AREA, "1", "mi2", "ac"))
        assertEquals("43560", conv(Category.AREA, "1", "ac", "ft2"))
    }

    @Test fun volume() {
        assertEquals("3.785411784", conv(Category.VOLUME, "1", "gal_us", "l"))
        assertEquals("128", conv(Category.VOLUME, "1", "gal_us", "floz_us"))
        assertEquals("160", conv(Category.VOLUME, "1", "gal_uk", "floz_uk"))
        assertEquals("3", conv(Category.VOLUME, "1", "tbsp_us", "tsp_us"))
        assertEquals("1728", conv(Category.VOLUME, "1", "ft3", "in3"))
        assertEquals("1000", conv(Category.VOLUME, "1", "m3", "l"))
    }

    @Test fun mass() {
        assertEquals("0.45359237", conv(Category.MASS, "1", "lb", "kg"))
        assertEquals("16", conv(Category.MASS, "1", "lb", "oz"))
        assertEquals("14", conv(Category.MASS, "1", "st", "lb"))
        assertEquals("2000", conv(Category.MASS, "1", "ton_us", "lb"))
        assertEquals("2240", conv(Category.MASS, "1", "ton_uk", "lb"))
        assertEquals("5", conv(Category.MASS, "1", "g", "ct"))
    }

    @Test fun temperatureOffsets() {
        assertEquals("32", conv(Category.TEMPERATURE, "0", "c", "f"))
        assertEquals("212", conv(Category.TEMPERATURE, "100", "c", "f"))
        assertEquals("-40", conv(Category.TEMPERATURE, "-40", "f", "c"))
        assertEquals("273.15", conv(Category.TEMPERATURE, "0", "c", "k"))
        assertEquals("-273.15", conv(Category.TEMPERATURE, "0", "k", "c"))
        assertEquals("-459.67", conv(Category.TEMPERATURE, "0", "k", "f"))
        assertEquals("491.67", conv(Category.TEMPERATURE, "0", "c", "r"))
        assertEquals("80", conv(Category.TEMPERATURE, "100", "c", "re"))
        assertEquals("37", conv(Category.TEMPERATURE, "98.6", "f", "c"))
        // An offset scale must not be treated as a plain factor: 10 °C -> 50 °F, not 18.
        assertEquals("50", conv(Category.TEMPERATURE, "10", "c", "f"))
    }

    @Test fun speedHasNoFloatNoise() {
        assertEquals("27.7777777778", conv(Category.SPEED, "100", "kmh", "mps"))
        assertEquals("100", conv(Category.SPEED, "27.77777777777777777777777777777778", "mps", "kmh"))
        assertEquals("1.609344", conv(Category.SPEED, "1", "mph", "kmh"))
        assertEquals("1.852", conv(Category.SPEED, "1", "kn", "kmh"))
        assertEquals("1079252848.8", conv(Category.SPEED, "1", "c", "kmh"))
    }

    @Test fun time() {
        assertEquals("86400", conv(Category.TIME, "1", "d", "s"))
        assertEquals("168", conv(Category.TIME, "1", "wk", "h"))
        assertEquals("365.2425", conv(Category.TIME, "1", "yr", "d"))
        assertEquals("12", conv(Category.TIME, "1", "yr", "mo"))
        assertEquals("1000000", conv(Category.TIME, "1", "ms", "ns"))
    }

    @Test fun digitalDecimalAndBinary() {
        assertEquals("8", conv(Category.DIGITAL, "1", "B", "bit"))
        assertEquals("1024", conv(Category.DIGITAL, "1", "MiB", "KiB"))
        assertEquals("1000", conv(Category.DIGITAL, "1", "GB", "MB"))
        assertEquals("0.931322574615", conv(Category.DIGITAL, "1", "GB", "GiB"))
        assertEquals("1.073741824", conv(Category.DIGITAL, "1", "GiB", "GB"))
        assertEquals("125", conv(Category.DIGITAL, "1", "Gbit", "MB"))
        assertEquals("1.12589990684", conv(Category.DIGITAL, "1", "PiB", "PB"))
    }

    @Test fun energy() {
        assertEquals("4.184", conv(Category.ENERGY, "1", "kcal", "kJ"))
        assertEquals("3600", conv(Category.ENERGY, "1", "kWh", "kJ"))
        assertEquals("1.602176634E-19", conv(Category.ENERGY, "1", "eV", "J"))
        assertEquals("1.05505585262", conv(Category.ENERGY, "1", "BTU", "kJ"))
        assertEquals("10000000", conv(Category.ENERGY, "1", "J", "erg"))
    }

    @Test fun pressure() {
        assertEquals("1.01325", conv(Category.PRESSURE, "1", "atm", "bar"))
        assertEquals("760", conv(Category.PRESSURE, "1", "atm", "torr"))
        assertEquals("14.6959487755", conv(Category.PRESSURE, "1", "atm", "psi"))
        assertEquals("6894.75729317", conv(Category.PRESSURE, "1", "psi", "Pa"))
        assertEquals("1000", conv(Category.PRESSURE, "1", "bar", "mbar"))
    }

    @Test fun angle() {
        assertEquals("3.14159265359", conv(Category.ANGLE, "180", "deg", "rad"))
        assertEquals("180", conv(Category.ANGLE, "3.14159265358979323846264338327950288419716939937510", "rad", "deg"))
        assertEquals("400", conv(Category.ANGLE, "1", "turn", "grad"))
        assertEquals("3600", conv(Category.ANGLE, "1", "deg", "arcsec"))
        assertEquals("6400", conv(Category.ANGLE, "1", "turn", "mil"))
    }

    @Test fun fuelEconomyInversion() {
        // 5 L/100 km = 20 km/L.
        assertEquals("20", conv(Category.FUEL, "5", "l100km", "kmpl"))
        assertEquals("5", conv(Category.FUEL, "20", "kmpl", "l100km"))
        // 10 L/100 km = 23.5214583 mpg (US).
        assertEquals("23.5214583", conv(Category.FUEL, "10", "l100km", "mpg_us", 9))
        assertEquals("28.2480936", conv(Category.FUEL, "10", "l100km", "mpg_uk", 9))
        assertEquals("10", conv(Category.FUEL, "23.52145833333333", "mpg_us", "l100km", 9))
        // 0 L/100 km is an infinite distance per litre: undefined, not a crash.
        assertEquals("null", conv(Category.FUEL, "0", "l100km", "kmpl"))
        assertEquals("null", conv(Category.FUEL, "0", "mpg_us", "l100km"))
        // Same unit to same unit is identity, even for reciprocal units.
        assertEquals("0", conv(Category.FUEL, "0", "l100km", "l100km"))
    }

    @Test fun frequency() {
        assertEquals("1000000", conv(Category.FREQUENCY, "1", "MHz", "Hz"))
        assertEquals("1", conv(Category.FREQUENCY, "60", "rpm", "Hz"))
        assertEquals("6.28318530718", conv(Category.FREQUENCY, "1", "Hz", "rads"))
    }

    @Test fun parsing() {
        assertEquals(BigDecimal("12.5"), NumberFormat.parse("12,5"))
        assertEquals(BigDecimal("-3"), NumberFormat.parse(" -3 "))
        assertEquals(BigDecimal("1.5E3"), NumberFormat.parse("1.5e3"))
        assertNull(NumberFormat.parse(""))
        assertNull(NumberFormat.parse("-"))
        assertNull(NumberFormat.parse("1.2.3"))
        assertNull(NumberFormat.parse("abc"))
    }

    @Test fun formatting() {
        assertEquals("0", NumberFormat.format(BigDecimal("0.000")))
        assertEquals("1.5", NumberFormat.format(BigDecimal("1.50000")))
        assertEquals("100", NumberFormat.format(BigDecimal("1E2")))
        assertEquals("0.333333333333", NumberFormat.format(BigDecimal.ONE.divide(BigDecimal("3"), MC)))
        assertEquals("1.23E+20", NumberFormat.format(BigDecimal("123000000000000000000")))
        assertEquals("-2.5E-9", NumberFormat.format(BigDecimal("-0.0000000025")))
        assertEquals("123456789012345", NumberFormat.format(BigDecimal("123456789012345"), 15))
    }

    @Test fun numberBases() {
        assertEquals(BaseConverter.Parsed.Ok(BigInteger.valueOf(255)), BaseConverter.parse("ff", 16))
        assertEquals(BaseConverter.Parsed.Ok(BigInteger.valueOf(255)), BaseConverter.parse("0xFF", 16))
        assertEquals(BaseConverter.Parsed.Ok(BigInteger.valueOf(5)), BaseConverter.parse("0b101", 2))
        assertEquals(BaseConverter.Parsed.Ok(BigInteger.valueOf(-8)), BaseConverter.parse("-10", 8))
        assertEquals(BaseConverter.Parsed.Ok(BigInteger.valueOf(1_000_000)), BaseConverter.parse("1_000 000", 10))
        assertEquals(BaseConverter.Parsed.Empty, BaseConverter.parse("  ", 10))
        val bad = BaseConverter.parse("102", 2)
        assertTrue(bad is BaseConverter.Parsed.Invalid && bad.message.contains("'2'"))
        assertTrue(BaseConverter.parse("9", 8) is BaseConverter.Parsed.Invalid)
        assertTrue(BaseConverter.parse("G", 16) is BaseConverter.Parsed.Invalid)
        assertTrue(BaseConverter.parse("0x", 16) is BaseConverter.Parsed.Invalid)

        val big = BigInteger("18446744073709551615") // 2^64 - 1, beyond Long
        assertEquals("FFFFFFFFFFFFFFFF", BaseConverter.format(big, 16))
        assertEquals("1777777777777777777777", BaseConverter.format(big, 8))
        assertEquals("1111 0000", BaseConverter.format(BigInteger.valueOf(240), 2, grouped = true))
        assertEquals("1,234,567", BaseConverter.format(BigInteger.valueOf(1234567), 10, grouped = true))
        assertEquals("-FF", BaseConverter.format(BigInteger.valueOf(-255), 16))
        assertEquals("0", BaseConverter.format(BigInteger.ZERO, 2))
    }

    @Test fun recentsCodec() {
        val a = RecentConversion(Category.LENGTH, "m", "ft", "1.5")
        val b = RecentConversion(Category.NUMBER_BASE, "16", "10", "FF")
        val list = RecentsCodec.push(RecentsCodec.push(emptyList(), a), b)
        assertEquals(listOf(b, a), list)
        assertEquals(list, RecentsCodec.decode(RecentsCodec.encode(list)))
        // Same category and units replaces the old entry and moves it to the front.
        val a2 = a.copy(input = "3")
        assertEquals(listOf(a2, b), RecentsCodec.push(list, a2))
        // Bounded size; garbage lines ignored.
        var many = emptyList<RecentConversion>()
        repeat(30) { many = RecentsCodec.push(many, RecentConversion(Category.MASS, "kg", "u$it", "$it")) }
        assertEquals(RecentsCodec.MAX, many.size)
        assertEquals(emptyList<RecentConversion>(), RecentsCodec.decode("nonsense\nLENGTH\tm"))
        assertEquals(emptyList<RecentConversion>(), RecentsCodec.decode(null))
    }
}
