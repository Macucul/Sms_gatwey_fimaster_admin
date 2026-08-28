# 📋 Guia de Integração e Segurança: Portal FiMaster & SMS Gateway
Este documento serve como a especificação técnica oficial para compartilhar com sucesso o mesmo banco de dados (Firebase Realtime Database / Firestore / GitHub) entre o **SMS Gateway (Android Admin)**, o **Portal FiMaster (Cliente Android/iOS)** e os **Robôs MQL5 (MetaTrader 5)**.

---

## 🔐 1. Arquitetura de Segurança e Regras do Firebase

Para garantir que apenas administradores autenticados possam modificar os dados dos utilizadores e licenças, enquanto os clientes e robôs podem ler os dados de validação de forma segura e eficiente, as regras do Firebase devem ser configuradas exatamente como detalhado abaixo.

### 🗄️ Firebase Realtime Database (`database.rules.json`)
Cole esta regra no painel **Database Rules** do Realtime Database:

```json
{
  "rules": {
    "dados": {
      "usuarios": {
        // Qualquer pessoa ou robô pode ler os dados de utilizadores para validação offline/robô
        ".read": "true",
        "$userId": {
          // Apenas o Administrador autenticado pelo UID especificado pode escrever
          ".write": "auth != null && auth.uid == 'SUA_UID_DE_ADMIN_AQUI'"
        }
      },
      "indices": {
        // Qualquer pessoa ou robô pode ler os índices de telefones e MT5 para mapeamento rápido
        ".read": "true",
        // Apenas o Administrador autenticado pelo UID especificado pode escrever
        ".write": "auth != null && auth.uid == 'SUA_UID_DE_ADMIN_AQUI'"
      },
      "parametros": {
        // Qualquer robô pode ler as configurações de parâmetros
        ".read": "true",
        "$mt5Id": {
          // Apenas o Administrador autenticado pelo UID especificado pode escrever
          ".write": "auth != null && auth.uid == 'SUA_UID_DE_ADMIN_AQUI'"
        }
      },
      "versao": {
        ".read": "true",
        ".write": "auth != null && auth.uid == 'SUA_UID_DE_ADMIN_AQUI'"
      }
    }
  }
}
```

### 🔥 Cloud Firestore (`firestore.rules`)
Se utilizar o Firestore para coleções de dados, aplique as seguintes regras de segurança:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Função auxiliar para verificar se o utilizador é o administrador
    function isAdmin() {
      return request.auth != null && request.auth.uid == 'SUA_UID_DE_ADMIN_AQUI';
    }

    // Regras para a coleção principal de utilizadores e históricos
    match /dados_usuarios/{userId} {
      allow read: if true; // Robôs MQL5 e o Painel precisam ler para validar licenças
      allow write: if isAdmin(); // Apenas o administrador autenticado pode alterar os registos
    }

    // Regras para os índices gerados
    match /dados_indices/{document} {
      allow read: if true; // Robôs necessitam de ler os índices de mapeamento (ex: mt5.json, telefones.json)
      allow write: if isAdmin(); // Apenas o administrador autenticado pode reconstruir ou modificar índices
    }

    // Regras para parâmetros de configuração do EA por conta MT5
    match /dados_parametros/{mt5Id} {
      allow read: if true; // O robô precisa ler o seu ficheiro correspondente
      allow write: if isAdmin(); // Apenas o administrador autenticado pode salvar novos parâmetros
    }

    // Regra padrão de segurança para qualquer outra coleção não especificada
    match /{document=**} {
      allow read, write: if isAdmin();
    }
  }
}
```

---

## 📂 2. Estrutura de Caminhos e JSON Schemas

### 👤 Caminho do Utilizador (`dados/usuarios/{id_usuario}.json`)
Este ficheiro armazena as informações completas do utilizador, a sua licença, status de reembolso, dados de auditoria de dispositivo e autorização.

```json
{
  "USER_ID_AQUI": {
    "senha_hash": "64_CHARACTER_SHA256_HASH_AQUI",
    "validade": "YYYY-MM-DD",
    "numero": "+351912345678",
    "nome": "Nome do Cliente",
    "origem": "PAYMENT_GATEWAY",
    "status": "ATIVO",
    "data_registro": "YYYY-MM-DDTHH:MM:SSZ",
    "ultima_atualizacao": "YYYY-MM-DDTHH:MM:SSZ",
    "id_transacao": "TX_999999999",
    "saldo": 100.0,
    "salt": "RANDOM_SALT_STRING",
    "token_recuperacao": "RESET_TOKEN_AQUI",
    "nivel_autorizacao": "CLIENTE",
    "mt5": {
      "registrado": true,
      "id_conta": "887766"
    },
    "licenca": {
      "ativa": true,
      "produto": "EA_FIMASTER_PRO",
      "plano": "ANUAL",
      "validade": "YYYY-MM-DD",
      "ultima_renovacao": "YYYY-MM-DD",
      "total_renovacoes": 1,
      "historico": [
        {
          "data": "YYYY-MM-DD",
          "id_transacao": "TX_999999999",
          "valor": 100.0
        }
      ]
    },
    "reembolso": {
      "solicitado": false,
      "status": "NENHUM",
      "id_reembolso": "",
      "data_solicitacao": "",
      "data_aprovacao": "",
      "data_pagamento": ""
    },
    "auditoria": {
      "ultimo_login": "YYYY-MM-DDTHH:MM:SSZ",
      "ultimo_dispositivo": "DISPOSITIVO_UID_OU_ANDROID_ID_AQUI",
      "tentativas_login": 0
    },
    "autorizacao": {
      "status": "APROVADO",
      "aprovado_por": "ADMIN_UID",
      "data_aprovacao": "YYYY-MM-DDTHH:MM:SSZ",
      "motivo": ""
    }
  }
}
```

### 📈 Índice de Mapeamento MT5 (`dados/indices/mt5.json`)
Responsável pelo mapeamento rápido que associa o número da conta do MetaTrader 5 do utilizador à conta cadastral principal:

```json
{
  "887766": {
    "usuario": "USER_ID_AQUI",
    "telefone": "+351912345678",
    "nome": "Nome do Cliente",
    "licenca_ativa": true,
    "validade": "YYYY-MM-DD",
    "status": "ativo"
  }
}
```

### 📞 Índice de Mapeamento de Telefones (`dados/indices/telefones.json`)
Associa o número de telemóvel do utilizador ao seu ID cadastral interno:

```json
{
  "+351912345678": {
    "usuario": "USER_ID_AQUI",
    "mt5": "887766",
    "status": "ATIVO"
  }
}
```

---

## 🔒 3. Mecanismo de Segurança Silenciosa (Mapeamento de Dispositivo)

O **SMS Gateway (Admin)** implementa um fluxo de verificação de dispositivo silencioso ao validar o acesso do utilizador. O **Portal FiMaster (Cliente)** deve utilizar exatamente o mesmo identificador de segurança silenciosa para validar o login do utilizador na aplicação cliente.

### 📱 Como funciona o fluxo:
1. Ao efetuar o login bem-sucedido com palavra-passe, a aplicação cliente recolhe o identificador único do utilizador atual.
2. **Método de Captura de UID**:
   - Se o utilizador estiver autenticado no Firebase Auth: Obtém o `FirebaseUser.getUid()`.
   - Se o Firebase Auth não estiver em uso ou não inicializado: Obtém o identificador único do dispositivo Android de forma persistente (`Settings.Secure.ANDROID_ID`).
3. O identificador recolhido é guardado no campo `deviceId` e enviado para o servidor Firebase sob a estrutura:
   `dados/usuarios/{userId}/auditoria/ultimo_dispositivo`
4. Se o identificador no servidor não corresponder ao ID do dispositivo atual, a aplicação cliente pode bloquear o acesso concorrente não autorizado.

### 🛠️ Código Kotlin de Referência para o Portal FiMaster:
Utilize este bloco de código no seu ViewModel de Login do Portal FiMaster para atualizar e verificar o ID do dispositivo de forma idêntica ao SMS Gateway:

```kotlin
import android.content.Context
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Função para obter o UID silencioso do utilizador ou dispositivo
 */
fun getSilentUid(context: Context): String {
    return try {
        FirebaseAuth.getInstance().currentUser?.uid
    } catch (e: Exception) {
        null
    } ?: Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
}

/**
 * Função exemplo para validação local e sincronização remota (idêntica ao Gateway)
 */
suspend fun verificarEAtualizarDispositivo(
    context: Context, 
    idUsuario: String, 
    storedDeviceId: String, 
    onSyncRequired: suspend (String) -> Unit
) = withContext(Dispatchers.IO) {
    val silentUid = getSilentUid(context)
    
    if (storedDeviceId.isEmpty() || storedDeviceId != silentUid) {
        // Dispositivo novo ou alterado - Registar silenciosamente
        onSyncRequired(silentUid)
    }
}
```

---

## 🔑 4. Criptografia e Assinatura Digital de Senhas

Os robôs MQL5 e a aplicação cliente validam a integridade da palavra-passe com base em assinaturas baseadas no algoritmo **SHA-256** aliado a um `salt` dinâmico atribuído individualmente a cada utilizador no momento da criação da conta.

1. **Geração do Salt**: String aleatória gerada e guardada no registo do utilizador (`salt`).
2. **Hash Esperado**: `SHA-256(senha_inserida + salt)`.
3. **Formato**: A palavra-passe local inserida pelo cliente é concatenada com o `salt` remoto do utilizador antes do hash ser calculado. O robô faz a correspondência exata comparando o resultado obtido com o hash guardado no servidor em `senha_hash`.

---

## 🤖 5. Prompt de IA para Desenvolvimento do "Portal FiMaster"
Copie e cole o prompt abaixo no seu assistente de desenvolvimento ao iniciar o desenvolvimento da aplicação **Portal FiMaster**:

> **PROMPT DE INICIALIZAÇÃO:**
> "Estamos a construir a aplicação cliente **Portal FiMaster** em Kotlin e Jetpack Compose. Esta aplicação partilha o mesmo Firebase Realtime Database que a nossa aplicação administrativa SMS Gateway.
> 
> Implemente o fluxo de login de forma que:
> 1. Busque o registo do utilizador em `dados/usuarios/{id_usuario}.json`.
> 2. Valide as credenciais localmente calculando o hash `SHA-256` da palavra-passe inserida somada ao `salt` remoto encontrado no JSON do utilizador. Compare o resultado com o campo `senha_hash`.
> 3. Implemente a **Segurança Silenciosa**: capture o UID atual utilizando `FirebaseAuth.getInstance().currentUser?.uid` (se disponível) ou `Settings.Secure.ANDROID_ID` como fallback.
> 4. Guarde o UID do dispositivo silencioso no campo `auditoria.ultimo_dispositivo` (também mapeado localmente no Room Database como `deviceId`) e sincronize a atualização com o Firebase Realtime Database para auditoria imediata.
> 5. Siga as convenções de design Material 3 e arquitetura MVVM limpa."

---
*Este documento é gerado automaticamente e reflete com precisão os padrões de segurança e dados implementados no ecossistema FiMaster.*
