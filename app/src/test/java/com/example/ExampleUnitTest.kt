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

  @Test
  fun testUserJsonStructure_directFormat() {
    val json = """
      {
        "id_usuario": "USR-882194",
        "senha_hash": "ABCDEF123456",
        "numero": "+258841234567",
        "nome": "Cliente Teste",
        "status": "ATIVO",
        "saldo": 500.0
      }
    """.trimIndent()
    val root = org.json.JSONObject(json)
    assertTrue(root.has("id_usuario"))
    assertEquals("USR-882194", root.getString("id_usuario"))
  }
}
