# 🤖 Nós e Fluxo de Autenticação / Login do Robô EA MT5 (MQL5)

Este documento descreve detalhadamente todos os **nós**, **endpoints**, **estruturas JSON** e o **passo a passo de login/autenticação** que o **Expert Advisor (EA) MetaTrader 5 (MQL5)** acessa no Firebase (Realtime Database, Firestore ou API REST) para autenticar a conta de trading, validar licenças e descarregar parâmetros operacionais.

---

## 📌 1. Visão Geral do Fluxo de Login do EA MT5

O login do robô EA no MetaTrader 5 é executado em 3 etapas sequenciais de segurança:

```
[EA MT5 Inicia] 
      │
      ▼
 1. Consulta Índice MT5 ────────► dados/indices/mt5.json (ou nó dados/indices/mt5)
      │                           (Obtém o ID do Usuário vinculado: "USR-...")
      ▼
 2. Consulta Dados do Usuário ──► dados/usuarios/{idUsuario}.json
      │                           (Obtém Salt, SenhaHash, Data de Validade e Status)
      ▼
 3. Validação Criptográfica ────► Hash SHA-256(Senha + Salt) == SenhaHash
      │
      ├── [Se Válido e Não Expirado] ──► Login Aprovado ✅
      │                                   Carrega Licença & Parâmetros (Passo 4 e 5)
      │
      └── [Se Inválido ou Expirado]  ──► Bloqueio de Execução ❌
```

---

## 🗂️ 2. Mapeamento Completo dos Nós Acessados pelo EA MT5

| Ordem | Caminho / Nó no Banco | Coleção Firestore | Tipo de Acesso | Finalidade |
| :---: | :--- | :--- | :---: | :--- |
| **1º** | `dados/indices/mt5` (`mt5.json`) | `dados_indices/mt5` | **Leitura** | Mapeia o número da conta MT5 (Login) para o `idUsuario`. |
| **2º** | `dados/usuarios/{idUsuario}` | `dados_usuarios/{idUsuario}` | **Leitura** | Validação de credenciais (Hash SHA-256 + Salt), data de expiração e status ativo. |
| **3º** | `dados/licencas/{mt5IdConta}` | `dados_licencas/{mt5IdConta}` | **Leitura** | Consulta direta da licença emitida para a conta MT5 específica. |
| **4º** | `dados/parametros/{mt5IdConta}` | `dados_parametros/{mt5IdConta}` | **Leitura** | Descarrega as configurações operacionais do robô (JSON / `.set`). |
| **5º** | `dados/indice/licenca` | `dados_indice/licenca` | **Leitura** | Regras e permissões do plano (templates, áudio, trailing stop, limites de contas). |
| **6º** | `dados/indices/instrucoes_admin_templates` | `dados_indices/instrucoes_admin_templates` | **Leitura** | Templates mestres de configuração operacional. |

---

## 🔍 3. Estrutura dos Dados em Cada Nó

### 1. Nó do Índice MT5
* **Caminho Firebase RTDB:** `dados/indices/mt5`
* **URL REST:** `https://<PROJECT-ID>.firebaseio.com/dados/indices/mt5.json`
* **Exemplo de Conteúdo:**
```json
{
  "10293847": {
    "usuario": "USR-1708459200000",
    "telefone": "+258841234567",
    "nome": "João Silva",
    "status": "ATIVO",
    "plano": "pro",
    "expiracao": "2026-12-31"
  },
  "55443322": {
    "usuario": "USR-1708461500000",
    "telefone": "+258869876543",
    "nome": "Carlos M.",
    "status": "ATIVO",
    "plano": "starter",
    "expiracao": "2026-09-30"
  }
}
```

---

### 2. Nó de Dados do Usuário
* **Caminho Firebase RTDB:** `dados/usuarios/{idUsuario}`
* **URL REST:** `https://<PROJECT-ID>.firebaseio.com/dados/usuarios/USR-1708459200000.json`
* **Exemplo de Conteúdo:**
```json
{
  "id_usuario": "USR-1708459200000",
  "nome": "João Silva",
  "telefone": "+258841234567",
  "mt5_id_conta": "10293847",
  "licenca_produto": "FiMaster EA Pro",
  "plano": "pro",
  "status_licenca": "ATIVA",
  "data_inicio": 1708459200000,
  "data_expiracao": 1767139200000,
  "data_expiracao_formatada": "31/12/2026",
  "salt": "a8f9c2d1e0b4",
  "senha_hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855:a8f9c2d1e0b4"
}
```

---

### 3. Nó de Consulta Direta de Licença do EA
* **Caminho Firebase RTDB:** `dados/licencas/{mt5IdConta}`
* **URL REST:** `https://<PROJECT-ID>.firebaseio.com/dados/licencas/10293847.json`
* **Exemplo de Conteúdo:**
```json
{
  "status": "AUTORIZADO",
  "mt5_conta": "10293847",
  "titular": "João Silva",
  "plano": "pro",
  "produto": "FiMaster EA Pro",
  "valido_ate": "31/12/2026",
  "timestamp_expiracao": 1767139200000,
  "recursos": {
    "templates": true,
    "captura_tela": true,
    "grafico_patrimonio": true,
    "audio": true,
    "vincular_contas_max": 2,
    "sala_vip": true
  }
}
```

---

### 4. Nó de Parâmetros Operacionais do EA MT5
* **Caminho Firebase RTDB:** `dados/parametros/{mt5IdConta}`
* **URL REST:** `https://<PROJECT-ID>.firebaseio.com/dados/parametros/10293847.json`
* **Exemplo de Conteúdo:**
```json
{
  "MT5_CONTA": "10293847",
  "EA_ATIVO": true,
  "EA_AUTO": true,
  "LOTE": 0.05,
  "STOP_LOSS_POINTS": 200,
  "TAKE_PROFIT_POINTS": 400,
  "TRAILING_STOP": true,
  "TRAILING_START": 150,
  "TRAILING_STEP": 50,
  "MAX_SPREAD": 25,
  "HORA_INICIO": "08:00",
  "HORA_FIM": "17:00",
  "FECHAR_FIM_DO_DIA": true
}
```

---

## 💻 4. Exemplo de Código MQL5 para Login e Autenticação no MT5

No MetaTrader 5, adicione a URL base do seu Firebase nas permissões de WebRequest:  
**Ferramentas > Opções > Expert Advisors > Permitir WebRequest para os URLs listados**.

```mql5
//+------------------------------------------------------------------+
//|                                                FiMaster_Auth.mqh |
//|                                    FiMaster EA Authentication    |
//+------------------------------------------------------------------+
#property strict

string FIREBASE_URL = "https://SEU-PROJETO-FIREBASE.firebaseio.com";

// Função para efetuar login do EA
bool RealizarLoginEA(long mt5Account, string eaPassword)
{
   string accountStr = IntegerToString(mt5Account);
   Print("Iniciando login do EA para a conta: ", accountStr);

   // 1. Passo 1: Buscar usuário no índice MT5
   string indexUrl = FIREBASE_URL + "/dados/indices/mt5/" + accountStr + ".json";
   string indexResponse = HttpGet(indexUrl);
   
   if(indexResponse == "null" || StringLen(indexResponse) == 0)
   {
      Alert("ERRO: Conta MT5 ", accountStr, " nao encontrada no indice de licencas.");
      return false;
   }
   
   // Extrair idUsuario do JSON (exemplo simplificado)
   string userId = ExtrairCampoJson(indexResponse, "usuario");
   if(StringLen(userId) == 0)
   {
      Alert("ERRO: ID de usuario nao vinculado a esta conta MT5.");
      return false;
   }

   // 2. Passo 2: Buscar credenciais e validade do usuário
   string userUrl = FIREBASE_URL + "/dados/usuarios/" + userId + ".json";
   string userResponse = HttpGet(userUrl);
   
   if(userResponse == "null" || StringLen(userResponse) == 0)
   {
      Alert("ERRO: Cadastro de usuario nao localizado no servidor.");
      return false;
   }
   
   // Extrair campos de segurança
   string salt = ExtrairCampoJson(userResponse, "salt");
   string senhaHashEsperada = ExtrairCampoJson(userResponse, "senha_hash");
   long dataExpiracao = (long)StringToInteger(ExtrairCampoJson(userResponse, "data_expiracao"));
   
   // 3. Passo 3: Validar expiração
   long tempoAtualMs = (long)TimeCurrent() * 1000;
   if(dataExpiracao > 0 && tempoAtualMs > dataExpiracao)
   {
      Alert("LICENCA EXPIRADA! Renove sua assinatura para continuar utilizando o EA.");
      return false;
   }
   
   // 4. Passo 4: Validar Senha Hash SHA-256
   string hashCalculada = GerarSha256(eaPassword + salt);
   
   // Comparar hash (senha_hash pode conter "hash:salt")
   string hashLimpa = StringSubstr(senhaHashEsperada, 0, 64);
   if(hashCalculada != hashLimpa)
   {
      Alert("ERRO DE AUTENTICACAO: Senha do EA incorreta.");
      return false;
   }
   
   Print("SUCCESS: EA MT5 autenticado com sucesso para a conta ", accountStr);
   return true;
}

// Função auxiliar HTTP GET no MQL5
string HttpGet(string url)
{
   char post[], result[];
   string headers = "Content-Type: application/json\r\n";
   int timeout = 5000;
   string resultHeaders;
   
   int res = WebRequest("GET", url, headers, timeout, post, result, resultHeaders);
   if(res == 200)
   {
      return CharArrayToString(result, 0, WHOLE_ARRAY, CP_UTF8);
   }
   Print("Falha na requisicao WebRequest HTTP: ", res);
   return "";
}
```

---

## 🔒 5. Regras de Segurança Mínimas Necessárias no Firebase

Para que o robô EA MT5 possa realizar a leitura dos nós de autenticação sem expor permissões de escrita a terceiros:

```json
{
  "rules": {
    "dados": {
      "indices": {
        "mt5": {
          ".read": true,
          ".write": "auth != null"
        }
      },
      "licencas": {
        ".read": true,
        ".write": "auth != null"
      },
      "parametros": {
        ".read": true,
        ".write": "auth != null"
      },
      "usuarios": {
        ".read": true,
        ".write": "auth != null"
      }
    }
  }
}
```
