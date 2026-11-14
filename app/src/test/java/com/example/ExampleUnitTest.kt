package com.example

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testSha256SecurityHashing() {
        val input = "2026"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val hash = bytes.joinToString("") { "%02x".format(it) }
        assertEquals(64, hash.length)
        assertNotNull(hash)
    }
}


