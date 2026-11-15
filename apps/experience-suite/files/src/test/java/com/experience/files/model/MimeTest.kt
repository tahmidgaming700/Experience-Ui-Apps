package com.experience.files.model

import com.experience.files.data.Mime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MimeTest {
    @Test fun pickerFilters() {
        assertTrue(Mime.matches("image/png", emptyList()))
        assertTrue(Mime.matches("image/png", listOf("image/*")))
        assertTrue(Mime.matches("application/pdf", listOf("image/*", "application/pdf")))
        assertTrue(Mime.matches("video/mp4", listOf("*/*")))
        assertFalse(Mime.matches("video/mp4", listOf("image/*")))
        assertFalse(Mime.matches("imagex/png", listOf("image/*")))
    }
}
