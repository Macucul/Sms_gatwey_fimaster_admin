package com.example.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object SecurityUtils {
    private const val PBKDF2_ITERATIONS = 100_000
    private const val PBKDF2_KEY_LENGTH = 256

    /**
     * Generates a random 6-character alphanumeric password containing only uppercase letters and numbers.
     */
    fun generateRandomPassword(length: Int = 6): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = SecureRandom()
        return (1..length)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }

    /**
     * Hashes password using PBKDF2WithHmacSHA256 (High Security).
     * Format: pbkdf2_sha256:iterations:saltHex:hashHex
     */
    fun hashPasswordPBKDF2(password: String, saltHex: String = generateSalt(), iterations: Int = PBKDF2_ITERATIONS): String {
        val saltBytes = saltHex.toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(password.toCharArray(), saltBytes, iterations, PBKDF2_KEY_LENGTH)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = skf.generateSecret(spec).encoded
        val hashHex = key.joinToString("") { "%02x".format(it) }
        return "pbkdf2:$iterations:$saltHex:$hashHex"
    }

    /**
     * Verifies password against PBKDF2 or legacy SHA-256 hash.
     */
    fun verifyPassword(password: String, storedHashWithSalt: String, salt: String = ""): Boolean {
        return try {
            if (storedHashWithSalt.startsWith("pbkdf2:")) {
                val parts = storedHashWithSalt.split(":")
                if (parts.size == 4) {
                    val iterations = parts[1].toInt()
                    val storedSalt = parts[2]
                    val candidate = hashPasswordPBKDF2(password, storedSalt, iterations)
                    constantTimeEquals(candidate, storedHashWithSalt)
                } else false
            } else {
                // Legacy SHA-256 comparison
                val effectiveSalt = if (salt.isNotEmpty()) salt else {
                    if (storedHashWithSalt.contains(":")) storedHashWithSalt.substringAfter(":") else ""
                }
                val cleanStoredHash = if (storedHashWithSalt.contains(":")) storedHashWithSalt.substringBefore(":") else storedHashWithSalt
                val candidateHash = hashSha256(password, effectiveSalt)
                constantTimeEquals(candidateHash, cleanStoredHash)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    /**
     * Hashes string using SHA-256 along with a salt (Legacy).
     */
    fun hashSha256(input: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val saltedInput = input + salt
        val hashBytes = digest.digest(saltedInput.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a random salt string.
     */
    fun generateSalt(): String {
        val random = SecureRandom()
        val bytes = ByteArray(8)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a random uppercase alphanumeric string.
     */
    fun generateRandomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = SecureRandom()
        return (1..length)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }
}
