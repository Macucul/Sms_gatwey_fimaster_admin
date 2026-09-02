# 🛡️ Guia de Nós, Acessos e Regras de Segurança do Firebase

Este documento detalha todos os nós, caminhos e coleções acessados pelo aplicativo **FIMASTER SMS Gateway / EA Manager** no **Firebase Realtime Database** e no **Cloud Firestore**, especificando o tipo de operação (leitura/escrita/exclusão) e fornecendo as regras de segurança prontas para cópia e colagem no Firebase Console.

---

## 1. 📋 Mapeamento Completo de Nós e Coleções

### 🟢 Realtime Database (RTDB) & Coleções Firestore

| Caminho no Repositório / RTDB | Coleção Firestore | Documento Firestore | Operação | Finalidade e Descrição |
| :--- | :--- | :--- | :--- | :--- |
| `dados/indices/licenca` | **Escrita / Leitura** | Planos e tabelas de preços de licenças (`starter`, `pro`, `master_vip`, `trial`), links de WhatsApp/Telegram, recursos e QR Codes de pagamento em binário/Base64. |
| `dados/indices/mt5` | **Escrita / Leitura** | Índice rápido mapeando números de contas MT5 para IDs de usuários (`"999999": "USR-..."`). |
| `dados/indices/telefones` | **Escrita / Leitura** | Índice rápido mapeando números de telefone para IDs de usuários (`"+25884...": "USR-..."`). |
| `dados/indices/instrucoes_admin_templates` | **Escrita / Leitura** | Templates mestres de configuração do EA, instruções JSON e parâmetros operacionais. |
| `dados/usuarios/{idUsuario}`  | **Escrita / Leitura** | Cadastro completo de usuários, planos adquiridos, saldo, expiração, MT5 ID e status de ativação. |
| `dados/licencas/{id}` | **Escrita / Leitura** | Dados da licença compilada para consulta do Expert Advisor (EA MQL5), indexada por `idUsuario`, `telefone` ou `mt5IdConta`. |
| `dados/parametros/{mt5IdConta}` | **Escrita / Leitura** | Parâmetros de trading do robô MT5 em formato JSON e `.set` (lotes, stop loss, take profit, trailing, horários). |
| `dados/parametros/ea_params` | **Escrita / Leitura** | Arquivo geral de parâmetros do EA (`ea_params.txt`). |
| `dados/pendentes/{idPendente}`| **Escrita / Leitura / Exclusão** | Fila de pagamentos pendentes recebidos por SMS (M-Pesa / E-Mola) aguardando vinculação de conta MT5. O item é excluído após a vinculação bem-sucedida. |
| `dados/reembolsos/{idReembolso}`| **Escrita / Leitura** | Registros de reembolsos e estornos operados no gateway. |
| `dados/configuracao/config`  | **Escrita / Leitura** | Configurações gerais do sistema SMS Gateway, chaves de API, webhooks e parâmetros globais. |
| `dados/auditoria/audit_log` |  **Escrita / Leitura** | Histórico e auditoria de eventos, sincronizações, disparos de SMS e requisições remotas. |
| `dados/versao`| **Escrita / Leitura** | Controle de versão de dados e status do sincronizador. |

---

## 2. 🔐 Regras de Segurança para o Firebase Realtime Database

Para aplicar no **Firebase Console** > **Realtime Database** > **Regras (Rules)**:

### Opção A: Regras de Produção Recomendadas (Autenticados / Anônimos)
> Permite que o app autenticado (mesmo anônimo) gerencie todos os nós de dados com segurança, bloqueando acessos públicos não identificados.

```json
{
  "rules": {
    "dados": {
      ".read": "auth != null",
      ".write": "auth != null",
      "indice": {
        "licenca": {
          ".read": true,
          ".write": "auth != null"
        }
      },
      "indices": {
        ".read": true,
        ".write": "auth != null"
      },
      "licencas": {
        ".read": true,
        ".write": "auth != null"
      },
      "parametros": {
        ".read": true,
        ".write": "auth != null"
      },
      "versao": {
        ".read": true,
        ".write": "auth != null"
      },
      "usuarios": {
        ".read": "auth != null",
        ".write": "auth != null"
      },
      "pendentes": {
        ".read": "auth != null",
        ".write": "auth != null"
      },
      "reembolsos": {
        ".read": "auth != null",
        ".write": "auth != null"
      },
      "configuracao": {
        ".read": "auth != null",
        ".write": "auth != null"
      },
      "auditoria": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    }
  }
}
```

### Opção B: Modo Permissivo (Desenvolvimento / Inicial)
```json
{
  "rules": {
    ".read": true,
    ".write": true
  }
}
```

---

## 3. 🛡️ Regras de Segurança para o Cloud Firestore

Para aplicar no **Firebase Console** > **Firestore Database** > **Regras (Rules)**:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Funções auxiliares de autenticação
    function isAuthenticated() {
      return request.auth != null;
    }

    // 1. Licenças públicas (leitura para robô EA/MQL5 e escrita para app autenticado)
    match /dados_indice/{document=**} {
      allow read: if true;
      allow write: if isAuthenticated();
    }
    
    match /dados_indices/{document=**} {
      allow read: if true;
      allow write: if isAuthenticated();
    }
    
    match /dados_licencas/{document=**} {
      allow read: if true;
      allow write: if isAuthenticated();
    }
    
    match /dados_parametros/{document=**} {
      allow read: if true;
      allow write: if isAuthenticated();
    }

    match /metadata/{document=**} {
      allow read: if true;
      allow write: if isAuthenticated();
    }
    
    match /dados_versao/{document=**} {
      allow read: if true;
      allow write: if isAuthenticated();
    }

    // 2. Dados Sensíveis (Usuários, Pendentes, Reembolsos, Auditoria, Configuração)
    match /dados_usuarios/{userId} {
      allow read, write: if isAuthenticated();
    }
    
    match /dados_pendentes/{pendingId} {
      allow read, write, delete: if isAuthenticated();
    }
    
    match /dados_reembolsos/{refundId} {
      allow read, write: if isAuthenticated();
    }
    
    match /dados_configuracao/{configId} {
      allow read, write: if isAuthenticated();
    }
    
    match /dados_auditoria/{auditId} {
      allow read, write: if isAuthenticated();
    }
  }
}
```

---

## 4. ⚙️ Como Ativar a Autenticação no Firebase Console

1. Abra o [Firebase Console](https://console.firebase.com/).
2. Selecione o projeto vinculado ao seu arquivo `google-services.json`.
3. No menu lateral esquerdo, vá em **Authentication** > **Sign-in method** (Método de login).
4. Localize a opção **Anônimo** (Anonymous) e clique em **Ativar** (Enable).
5. Salve as alterações.
