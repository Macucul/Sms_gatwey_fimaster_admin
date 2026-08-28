package com.example.util

import android.util.Log
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

data class ExtractedSmsData(
    val idTransacao: String,
    val valorRecebido: Double,
    val saldo: Double,
    val conta: String,
    val nome: String,
    val data: String,
    val hora: String
)

object SmsParser {
    private const val TAG = "SmsParser"

    fun cleanPhone(phone: String): String {
        val trimmed = phone.trim().removePrefix("+")
        return if (trimmed.startsWith("258")) {
            trimmed.substring(3)
        } else {
            trimmed
        }
    }

    /**
     * Normalizes a string by converting it to lowercase and stripping accents.
     */
    fun normalizeText(text: String): String {
        val temp = Normalizer.normalize(text, Normalizer.Form.NFD)
        val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        return pattern.matcher(temp).replaceAll("")
            .replace("ç", "c")
            .replace("Ç", "C")
            .trim()
    }

    private const val EMOLA_REGEX = """id da transacao:?\s*([a-z0-9_\-\.]+)\.?\s+recebeste\s+([0-9.,]+)\s*(?:mt)?\s+de\s+conta\s+([0-9]+),?\s*nome:?\s*(.*?)\s+as\s+([0-9:]+)\s+de\s+([0-9/]+)(?:\.?\s+o saldo da tua conta e\s+([0-9.,]+))?"""
    
    private const val MPESA_REGEX = """Confirmado\s+([a-z0-9]+)\.?\s+recebeste\s+([0-9.,]+)\s*(?:mt)?\s+de\s+([0-9]+)\s*-\s*(.*?)\s+aos\s+([0-9/]+)\s+as\s+([0-9:]+\s*(?:am|pm)?)(?:\.?\s+o teu novo saldo m-pesa e de\s+([0-9.,]+))?"""

    /**
     * Attempts to parse an SMS message to extract financial transaction details.
     * Uses the provided regex pattern with normalized text or fallback split mechanisms.
     */
    fun parseMessage(message: String, customRegexPattern: String? = null): ExtractedSmsData? {
        val normalized = normalizeText(message)
        Log.d(TAG, "Parsing Message: $message")
        Log.d(TAG, "Normalized: $normalized")

        // 1. Try Custom Regex if provided
        if (customRegexPattern != null) {
            try {
                val pattern = Pattern.compile(normalizeText(customRegexPattern), Pattern.CASE_INSENSITIVE)
                val matcher = pattern.matcher(normalized)
                if (matcher.find()) {
                    val groupCount = matcher.groupCount()
                    val idTransStr = if (groupCount >= 1) matcher.group(1)?.trim()?.uppercase(Locale.ROOT) ?: "" else ""
                    val valRecStr = if (groupCount >= 2) matcher.group(2)?.trim() ?: "0" else "0"
                    val accountStr = if (groupCount >= 3) matcher.group(3)?.trim() ?: "" else ""
                    val nameStr = if (groupCount >= 4) matcher.group(4)?.trim() ?: "" else ""
                    val timeStr = if (groupCount >= 5) matcher.group(5)?.trim() ?: "" else ""
                    val dateStr = if (groupCount >= 6) matcher.group(6)?.trim() ?: "" else ""
                    val balStr = if (groupCount >= 7) matcher.group(7)?.trim() ?: valRecStr else valRecStr

                    return ExtractedSmsData(
                        idTransacao = idTransStr,
                        valorRecebido = parseFormattedDouble(valRecStr),
                        saldo = parseFormattedDouble(balStr),
                        conta = cleanPhone(accountStr),
                        nome = cleanNameAndTitle(nameStr).uppercase(Locale.ROOT),
                        data = dateStr,
                        hora = timeStr.uppercase(Locale.ROOT)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Custom regex parsing failed: ${e.message}")
            }
        }

        // 2. Try E-mola standard pattern
        try {
            val emolaPattern = Pattern.compile(EMOLA_REGEX, Pattern.CASE_INSENSITIVE)
            val matcher = emolaPattern.matcher(normalized)
            if (matcher.find()) {
                val groupCount = matcher.groupCount()
                val transId = if (groupCount >= 1) matcher.group(1)?.trim()?.uppercase(Locale.ROOT) ?: "" else ""
                val rawVal = if (groupCount >= 2) matcher.group(2)?.trim() ?: "0" else "0"
                val rawAcc = if (groupCount >= 3) matcher.group(3)?.trim() ?: "" else ""
                val name = if (groupCount >= 4) matcher.group(4)?.trim() ?: "" else ""
                val time = if (groupCount >= 5) matcher.group(5)?.trim() ?: "" else ""
                val date = if (groupCount >= 6) matcher.group(6)?.trim() ?: "" else ""
                val rawBal = if (groupCount >= 7) matcher.group(7)?.trim() ?: rawVal else rawVal
                
                return ExtractedSmsData(
                    idTransacao = transId,
                    valorRecebido = parseFormattedDouble(rawVal),
                    saldo = parseFormattedDouble(rawBal),
                    conta = cleanPhone(rawAcc),
                    nome = cleanNameAndTitle(name).uppercase(Locale.ROOT),
                    data = date,
                    hora = time.uppercase(Locale.ROOT)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "E-mola regex pattern match failed: ${e.message}")
        }

        // 3. Try M-pesa standard pattern
        try {
            val mpesaPattern = Pattern.compile(MPESA_REGEX, Pattern.CASE_INSENSITIVE)
            val matcher = mpesaPattern.matcher(normalized)
            if (matcher.find()) {
                val groupCount = matcher.groupCount()
                val transId = if (groupCount >= 1) matcher.group(1)?.trim()?.uppercase(Locale.ROOT) ?: "" else ""
                val rawVal = if (groupCount >= 2) matcher.group(2)?.trim() ?: "0" else "0"
                val rawAcc = if (groupCount >= 3) matcher.group(3)?.trim() ?: "" else ""
                val name = if (groupCount >= 4) matcher.group(4)?.trim() ?: "" else ""
                val date = if (groupCount >= 5) matcher.group(5)?.trim() ?: "" else ""
                val time = if (groupCount >= 6) matcher.group(6)?.trim() ?: "" else ""
                val rawBal = if (groupCount >= 7) matcher.group(7)?.trim() ?: rawVal else rawVal
                
                return ExtractedSmsData(
                    idTransacao = transId,
                    valorRecebido = parseFormattedDouble(rawVal),
                    saldo = parseFormattedDouble(rawBal),
                    conta = cleanPhone(rawAcc),
                    nome = cleanNameAndTitle(name).uppercase(Locale.ROOT),
                    data = date,
                    hora = time.uppercase(Locale.ROOT)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "M-pesa regex pattern match failed: ${e.message}")
        }

        // --- STABLE FALLBACK PARSING SYSTEM ---
        
        // A. Try M-pesa Fallback if it contains m-pesa or confirmado
        if (normalized.contains("m-pesa", ignoreCase = true) || normalized.contains("confirmado", ignoreCase = true)) {
            try {
                var transId = ""
                var valor = 0.0
                var saldo = 0.0
                var conta = ""
                var nome = ""
                var data = ""
                var hora = ""
 
                val idxConfirmado = normalized.indexOf("confirmado", ignoreCase = true)
                if (idxConfirmado != -1) {
                    val startIdx = idxConfirmado + "confirmado".length
                    val sub = normalized.substring(startIdx).trim()
                    val endIdx = sub.indexOf(" ")
                    transId = if (endIdx != -1) sub.substring(0, endIdx).trim('.') else sub.trim('.')
                }
 
                val idxRecebeste = normalized.indexOf("recebeste", ignoreCase = true)
                if (idxRecebeste != -1) {
                    val start = idxRecebeste + "recebeste".length
                    val sub = normalized.substring(start).trim()
                    val deIdx = sub.indexOf(" de ", ignoreCase = true)
                    if (deIdx != -1) {
                        val valStr = sub.substring(0, deIdx).replace("mt", "", ignoreCase = true).trim()
                        valor = parseFormattedDouble(valStr)
 
                        val subRest = sub.substring(deIdx + " de ".length).trim()
                        val aosIdx = subRest.indexOf(" aos ", ignoreCase = true)
                        if (aosIdx != -1) {
                            val accAndName = subRest.substring(0, aosIdx).trim()
                            if (accAndName.contains("-")) {
                                val parts = accAndName.split("-")
                                conta = parts[0].trim()
                                nome = parts.drop(1).joinToString("-").trim()
                            } else {
                                val spaceIdx = accAndName.indexOf(" ")
                                if (spaceIdx != -1) {
                                    conta = accAndName.substring(0, spaceIdx).trim()
                                    nome = accAndName.substring(spaceIdx).trim()
                                } else {
                                    conta = accAndName
                                }
                            }
                            
                            val subDateTime = subRest.substring(aosIdx + " aos ".length).trim()
                            val asIdx = subDateTime.indexOf(" as ", ignoreCase = true)
                            if (asIdx != -1) {
                                data = subDateTime.substring(0, asIdx).trim()
                                val timeRest = subDateTime.substring(asIdx + " as ".length).trim()
                                val dotIdx = timeRest.indexOf(".")
                                hora = if (dotIdx != -1) timeRest.substring(0, dotIdx).trim() else timeRest.trim()
                            }
                        }
                    }
                }
 
                val idxNovoSaldo = normalized.indexOf("novo saldo m-pesa e de", ignoreCase = true)
                if (idxNovoSaldo != -1) {
                    val start = idxNovoSaldo + "novo saldo m-pesa e de".length
                    val sub = normalized.substring(start).trim()
                    val spaceIdx = sub.indexOf(" ")
                    val cleanBal = if (spaceIdx != -1) sub.substring(0, spaceIdx) else sub
                    saldo = parseFormattedDouble(cleanBal.replace("mt", "", ignoreCase = true).trim('.'))
                }
 
                if (transId.isNotEmpty() && conta.isNotEmpty() && valor > 0) {
                    return ExtractedSmsData(
                        idTransacao = transId.uppercase(Locale.ROOT),
                        valorRecebido = valor,
                        saldo = saldo,
                        conta = cleanPhone(conta),
                        nome = cleanNameAndTitle(nome).uppercase(Locale.ROOT),
                        data = data,
                        hora = hora.uppercase(Locale.ROOT)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "M-pesa fallback parsing failed: ${e.message}")
            }
        }
 
        // B. Try E-mola Fallback
        try {
            var transId = ""
            var valor = 0.0
            var saldo = 0.0
            var conta = ""
            var nome = ""
            var data = ""
            var hora = ""
 
            val idxTransacao = normalized.indexOf("id da transacao", ignoreCase = true)
            if (idxTransacao != -1) {
                val start = idxTransacao + "id da transacao".length
                val sub = normalized.substring(start).trim().removePrefix(":").trim()
                val end = sub.indexOf(" ")
                transId = if (end != -1) sub.substring(0, end).trim('.') else sub.trim('.')
            }
 
            val idxRecebeste = normalized.indexOf("recebeste", ignoreCase = true)
            if (idxRecebeste != -1) {
                val start = idxRecebeste + "recebeste".length
                val sub = normalized.substring(start).trim()
                val deIdx = sub.indexOf("de conta", ignoreCase = true)
                if (deIdx != -1) {
                    val valStr = sub.substring(0, deIdx).replace("mt", "", ignoreCase = true).trim()
                    valor = parseFormattedDouble(valStr)
 
                    val subAcc = sub.substring(deIdx + "de conta".length).trim()
                    val commaIdx = subAcc.indexOf(",")
                    val spaceIdx = subAcc.indexOf(" ")
                    val endAcc = if (commaIdx != -1 && commaIdx < spaceIdx) commaIdx else if (spaceIdx != -1) spaceIdx else subAcc.length
                    conta = subAcc.substring(0, endAcc).trim()
                }
            }
 
            val idxNome = normalized.indexOf("nome:", ignoreCase = true)
            if (idxNome != -1) {
                val start = idxNome + "nome:".length
                val sub = normalized.substring(start).trim()
                val asIdx = sub.indexOf(" as ", ignoreCase = true)
                if (asIdx != -1) {
                    nome = sub.substring(0, asIdx).trim()
 
                    val subTime = sub.substring(asIdx + " as ".length).trim()
                    val deDateIdx = subTime.indexOf(" de ", ignoreCase = true)
                    if (deDateIdx != -1) {
                        hora = subTime.substring(0, deDateIdx).trim()
 
                        val subDate = subTime.substring(deDateIdx + " de ".length).trim()
                        val spaceIdx = subDate.indexOf(" ")
                        val endIdx = if (spaceIdx != -1) spaceIdx else subDate.indexOf(".")
                        data = if (endIdx != -1) subDate.substring(0, endIdx).trim() else subDate.trim()
                    }
                }
            }
 
            val idxSaldo = normalized.indexOf("saldo da tua conta e", ignoreCase = true)
            if (idxSaldo != -1) {
                val start = idxSaldo + "saldo da tua conta e".length
                val sub = normalized.substring(start).trim()
                val selectEnd = sub.indexOf(" ")
                val cleanBal = if (selectEnd != -1) sub.substring(0, selectEnd) else sub
                saldo = parseFormattedDouble(cleanBal.replace("mt", "", ignoreCase = true).trim('.'))
            }
 
            if (transId.isNotEmpty() && conta.isNotEmpty() && nome.isNotEmpty()) {
                return ExtractedSmsData(
                    idTransacao = transId.uppercase(Locale.ROOT),
                    valorRecebido = valor,
                    saldo = saldo,
                    conta = cleanPhone(conta),
                    nome = cleanNameAndTitle(nome).uppercase(Locale.ROOT),
                    data = data,
                    hora = hora.uppercase(Locale.ROOT)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "E-mola fallback parsing failed: ${e.message}")
        }

        return null
    }

    private fun parseFormattedDouble(text: String): Double {
        val clean = text.replace("mt", "", ignoreCase = true)
            .replace(" ", "")
            .replace(",", "") // Strip thousand separator in American format, or handle Moçambique standard
            // Moçambique format is sometimes "1.250,00" or "1,250.00".
            // Let's standardise: if there is both "." and ",", we replace the thousands separator.
            // Let's count '.' and ',' to handle safely.
        
        return try {
            if (clean.contains(".") && clean.contains(",")) {
                // If comma comes last, e.g., 1.250,45 -> convert thousands "." to "" and "," to "."
                if (clean.lastIndexOf(",") > clean.lastIndexOf(".")) {
                    clean.replace(".", "").replace(",", ".").toDouble()
                } else {
                    // e.g., 1,250.45 -> convert thousands "," to ""
                    clean.replace(",", "").toDouble()
                }
            } else if (clean.contains(",")) {
                // Single separator. If it is 2 decimal digits like "1250,50", treat as decimal.
                val parts = clean.split(",")
                if (parts.size == 2 && parts[1].length <= 2) {
                    clean.replace(",", ".").toDouble()
                } else {
                    clean.replace(",", "").toDouble()
                }
            } else {
                clean.toDouble()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting double from text '$text': ${e.message}")
            0.0
        }
    }

    private fun cleanNameAndTitle(name: String): String {
        return name.replace(Regex("(?i)\\bas\\b"), "")
            .replace(".", "")
            .trim()
    }
}
