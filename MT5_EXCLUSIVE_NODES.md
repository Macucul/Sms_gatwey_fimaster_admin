# 📊 Nós Exclusivos que o Robô EA MetaTrader 5 (MT5) Acessa

Este documento descreve **exclusivamente os nós, rotas e estruturas de dados acessados pelo EA MT5** para autenticação, carregamento de parâmetros, eventos em tempo real, telemetria e sincronização de dados operacionais.

---

## 🎯 1. Mapa Resumo de Nós Acessados Exclusivamente pelo MT5

| Nó no Firebase / Endpoint REST | Método HTTP | Finalidade Operacional do EA MT5 |
| :--- | :---: | :--- |
| `dados/indices/mt5/{contaMT5}.json` | `GET` | **Autenticação (Passo 1)**: Identifica a qual usuário pertence a conta MT5. |
| `dados/usuarios/{idUsuario}.json` | `GET` | **Autenticação (Passo 2)**: Verifica validade da assinatura, status ativo e hash da senha. |
| `dados/licencas/{contaMT5}.json` | `GET` | **Validação de Licença**: Consulta permissões de recursos (áudio, templates, limites). |
| `dados/parametros/{contaMT5}.json` | `GET` | **Estratégia & Configurações**: Lotes, Stop Loss, Take Profit, Trailing e Horários em JSON. |
| `dados/parametros/{contaMT5}.set` | `GET` | **Preset MQL5**: Arquivo de setup padrão para carregar diretamente nos parâmetros do robô. |
| `dados/indices/instrucoes_admin_templates.json` | `GET` | **Templates de Operação**: Instruções mestres publicadas pelo administrador. |
| `dados/indice/licenca.json` | `GET` | **Catálogo de Recursos do Plano**: Tabela de regras operacionais de cada modalidade. |
| `dados/versao.json` | `GET` | **Controle de Versão**: Verifica se o robô precisa ser atualizado ou se os dados mudaram. |
| `dados/auditoria/audit_log.json` | `POST / PUT` | **Eventos & Telemetria**: Envio de logs de ordens abertas/fechadas, erros de execução e alertas. |

---

## 📋 2. Detalhamento e Formato de Dados por Nó

### 1️⃣ `dados/indices/mt5/{contaMT5}`
* **Objetivo:** O EA consulta este nó ao iniciar para checar se a conta logada no terminal possui permissão.
* **Exemplo de Resposta JSON:**
```json
{
  "usuario": "USR-1708459200000",
  "telefone": "+258841234567",
  "nome": "João Silva",
  "status": "ATIVO",
  "plano": "pro",
  "expiracao": "2026-12-31"
}
```

---

### 2️⃣ `dados/usuarios/{idUsuario}`
* **Objetivo:** Valida a integridade da licença, data de expiração em milissegundos e autenticação via hash SHA-256.
* **Exemplo de Resposta JSON:**
```json
{
  "id_usuario": "USR-1708459200000",
  "nome": "João Silva",
  "telefone": "+258841234567",
  "mt5_id_conta": "10293847",
  "plano": "pro",
  "status_licenca": "ATIVA",
  "data_expiracao": 1767139200000,
  "data_expiracao_formatada": "31/12/2026",
  "salt": "a8f9c2d1e0b4",
  "senha_hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855:a8f9c2d1e0b4"
}
```

---

### 3️⃣ `dados/licencas/{contaMT5}`
* **Objetivo:** Permite que o robô confira os módulos permitidos para o cliente (ex: se tem direito a áudio, gráficos ou templates).
* **Exemplo de Resposta JSON:**
```json
{
  "status": "AUTORIZADO",
  "mt5_conta": "10293847",
  "titular": "João Silva",
  "plano": "pro",
  "produto": "FiMaster EA Pro",
  "valido_ate": "31/12/2026",
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

### 4️⃣ `dados/parametros/{contaMT5}.json` & `.set`
* **Objetivo:** Parâmetros operacionais individuais definidos pelo usuário ou pelo gestor no aplicativo.
* **Exemplo de Resposta JSON:**
```json
{
  "MT5_CONTA": "10293847",
  "EA_ATIVO": true,
  "EA_AUTO": true,
  "MAGIC_NUMBER": 8882026,
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

### 5️⃣ `dados/indices/instrucoes_admin_templates.json`
* **Objetivo:** Carregar os modelos estratégicos e parâmetros globais definidos no painel do administrador.
* **Exemplo de Resposta JSON:**
```json
{
  "versao_minima_ea": "3.5.0",
  "atualizacao_forcada": false,
  "pares_permitidos": ["EURUSD", "GBPUSD", "XAUUSD", "Boom 1000", "Crash 1000"],
  "horario_noticias_bloquear_minutos": 15,
  "modos_execucao": {
    "conservador": { "lote_base": 0.01, "fator_recuperacao": 1.0, "max_drawdown_pct": 5.0 },
    "moderado": { "lote_base": 0.02, "fator_recuperacao": 1.2, "max_drawdown_pct": 10.0 },
    "agressivo": { "lote_base": 0.05, "fator_recuperacao": 1.5, "max_drawdown_pct": 20.0 }
  }
}
```

---

### 6️⃣ `dados/auditoria/audit_log.json` (Eventos & Telemetria do EA)
* **Objetivo:** Registro de eventos e histórico de operações enviados pelo MT5 para o painel de controle.
* **Payload de Envio do MT5:**
```json
{
  "timestamp": 1708460000000,
  "origem": "EA_MT5",
  "conta_mt5": "10293847",
  "tipo_evento": "ORDEM_EXECUTADA",
  "detalhes": {
    "ticket": 12893821,
    "ativo": "XAUUSD",
    "tipo": "BUY",
    "lote": 0.05,
    "preco_abertura": 2650.50,
    "stop_loss": 2640.00,
    "take_profit": 2670.00
  }
}
```

---

## 🔒 3. Regra de Segurança Firebase Específica para o MT5

Esta regra concede **leitura pública** apenas nos nós que o robô precisa consultar e **escrita no nó de auditoria/eventos**, mantendo os demais dados sensíveis do painel protegidos:

```json
{
  "rules": {
    "dados": {
      "indices": {
        "mt5": { ".read": true, ".write": "auth != null" },
        "instrucoes_admin_templates": { ".read": true, ".write": "auth != null" }
      },
      "usuarios": {
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
      "indice": {
        "licenca": { ".read": true, ".write": "auth != null" }
      },
      "versao": {
        ".read": true,
        ".write": "auth != null"
      },
      "auditoria": {
        ".read": true,
        ".write": true
      }
    }
  }
}
```
