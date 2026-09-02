package com.example

import com.example.util.SecurityUtils
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SecurityUtils and core logic.
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testPasswordGeneration_is6CharsUppercaseAlphanumeric() {
    val password = SecurityUtils.generateRandomPassword()
    assertEquals(6, password.length)
    assertTrue("Password should only contain uppercase letters and digits: $password", password.matches(Regex("^[A-Z0-9]{6}$")))
  }
}
