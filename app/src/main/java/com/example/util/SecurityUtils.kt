package com.example.util

import java.security.MessageDigest
import java.security.SecureRandom

object SecurityUtils {
    /**
     * Generates a secure, random alphanumeric password with special characters.
     */
    fun generateRandomPassword(length: Int = 12): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#%^&*"
        val random = SecureRandom()
        return (1..length)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }

    /**
     * Hashes string using SHA-256 along with a salt.
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
