# INSTRUÇÕES PARA PUBLICAÇÃO DE TEMPLATES DO ADMINISTRADOR MASTER

Este documento contém o guia completo, as especificações detalhadas, a lista completa de **ENUMERADORES (ENUMS)** e o schema JSON padronizado para a publicação de templates do robô (EA) no Firebase Realtime Database.

---

## 1. Endereço do Nó no Firebase Realtime Database

O administrador deve publicar os templates no seguinte nó do Firebase:

- `/dados/instrucoes_admin_templates.json`

---

## 2. LISTA COMPLETA DE ENUMERADORES (ENUMS) E VALORES VÁLIDOS

Para garantir o perfeito funcionamento da integração MQL5 no MetaTrader 5 e no aplicativo, os parâmetros que utilizam enumeradores ou booleanos devem utilizar **EXATAMENTE** os valores listados abaixo.

### A. Linhas de Equador (`LINHAS_DE_EQUADOR`)
Determina a exibição visual e o cálculo das linhas divisórias de equador no gráfico MT5.
- `true` -> Exibir Linhas de Equador no gráfico.
- `false` -> Ocultar Linhas de Equador.

### B. Esquema de Cores MQL5 (`ESQUEMA_CORES_ENUM`)
Define a paleta visual dos canais e linhas aplicados no gráfico MT5.
- `"CYAN_NEON"` -> Cyan Neon 🩵 (Canais `#22D3EE`, Linhas `#FF00E5`, Equador `#FFFF00`)
- `"DARK_MATRIX"` -> Dark Matrix 🟢 (Canais `#00FF66`, Linhas `#008000`, Equador `#00FFCC`)
- `"GOLDEN_PRO"` -> Golden Pro 🟡 (Canais `#FFD700`, Linhas `#FF8C00`, Equador `#FFFFFF`)
- `"PURPLE_NIGHT"` -> Purple Night 💜 (Canais `#A855F7`, Linhas `#EC4899`, Equador `#38BDF8`)
- `"CLASSIC_BLUE"` -> Classic Blue 💙 (Canais `#3B82F6`, Linhas `#1D4ED8`, Equador `#60A5FA`)
- `"CUSTOM"` -> Personalizado (Permite cores hexadecimais customizadas em `cor_de_canal`, `cor_de_linhas` e `corr_de_equador`)

### C. Tendência de Entrada (`TREND`)
Define o viés de tendência inicial esperado para as operações de entrada.
- `"TENDENCIA_DE_ALTA"` (ou `"UP_TREND"`) -> Tendência de Alta 🟢
- `"TENDENCIA_DE_BAIXA"` (ou `"DOWN_TREND"`) -> Tendência de Baixa 🔴

### D. Estratégia Operacional (`ESTRATÉGIA`)
Determina a mecânica de cálculo das ordens MQL5.
- `"FIMATHE"` -> Estratégia Fimathe Tradicional com expansão e subcanal.
- `"F_SURFADA"` -> Estratégia F_Surfada para acompanhamento de grandes tendências.

### E. Período Operacional MQL5 (`OperationalPeriod`)
Timeframe do gráfico MetaTrader 5 onde a estratégia é executada.
- `"PERIOD_M1"` -> Gráfico de 1 Minuto
- `"PERIOD_M5"` -> Gráfico de 5 Minutos
- `"PERIOD_M15"` -> Gráfico de 15 Minutos (Recomendado Padrão)
- `"PERIOD_M30"` -> Gráfico de 30 Minutos
- `"PERIOD_H1"` -> Gráfico de 1 Hora
- `"PERIOD_H4"` -> Gráfico de 4 Horas
- `"PERIOD_D1"` -> Gráfico Diário

### F. Período Automático (`AUTO_PERIOD`)
Frequência do ciclo automático de reajuste de canais e expansão.
- `"MANUAL"` -> Ajuste manual sem automação de tempo.
- `"SESSOES"` -> Sincronizado pelas Sessões de Mercado Forex.
- `"SEMANAL"` -> Sincronização semanal.
- `"DIARIO"` -> Sincronização diária.
- `"HORAS_8"` -> Reajuste a cada 8 horas.
- `"HORA_1"` -> Reajuste a cada 1 hora.

### G. Flags Booleans do EA (Verdadeiro/Falso: `true` ou `false`)
- `EA_ATIVO` -> Ativar/Desativar execução geral do EA.
- `EA_AUTO` -> Ativar/Desativar Automação MQL5.
- `AUTO_SURFADA` -> Ativar/Desativar Auto Surfada PCM.
- `virada_de_jogo` -> Ativar/Desativar Virada de Jogo.
- `Costurar` -> Ativar/Desativar Operações de Costura/Hedge.
- `TEMA` -> Ativar/Desativar Média Móvel 9/21.
- `SESSAO_ASIA_TOQUIO` -> Ativar/Desativar negociação na Sessão de Tóquio.
- `SESSAO_LONDRES` -> Ativar/Desativar negociação na Sessão de Londres.
- `SESSAO_NOVA_YORQUI` -> Ativar/Desativar negociação na Sessão de Nova Iorque.
- `GERENCIAMENTO_DE_RISCO_DIARIO` -> Ativar controle de risco diário.
- `GERENCIAMENTO_DE_RISCO_SEMANAL` -> Ativar controle de risco semanal.
- `Modify_Sl_For_OxO` -> Mover Stop Loss para Zero a Zero (0x0).
- `condicao_De_rompimento_c` -> Condição de rompimento para compra.
- `condicao_De_rompimento_v` -> Condição de rompimento para venda.
- `ativar_ou_desativar_compra` -> Permissão de ordens de Compra.
- `ativar_ou_desativar_venda` -> Permissão de ordens de Venda.
- `GMAIL` -> Alertas por e-mail.
- `notific` -> Notificações push MT5.

---

## 3. ORGANIZAÇÃO VISUAL E DIVISÃO DOS PARÂMETROS NO APLICATIVO

Para que o Administrador entenda exatamente como as configurações do objeto `"config"` serão apresentadas ao usuário final no aplicativo, todos os parâmetros são organizados automaticamente nas **9 Seções Visuais** da aba **Config EA**:

| Seção / Categoria Visual no App | Parâmetros do Objeto `"config"` pertencentes à Seção |
| :--- | :--- |
| **1. Autenticação & Expiração** | `mt5AccountId`, `SENHA` |
| **2. Esquema de Cores** | `ESQUEMA_CORES_ENUM`, `cor_de_canal`, `cor_de_linhas`, `corr_de_equador`, `LINHAS_DE_EQUADOR` |
| **3. Canais de Tendência** | `TREND`, `M_equador_alta`, `M_equador_baixa` |
| **4. Estratégia Principal** | `ESTRATÉGIA`, `OperationalPeriod`, `virada_de_jogo`, `Nives`, `Costurar`, `TEMA` |
| **5. Automação & Sessões** | `EA_ATIVO`, `EA_AUTO`, `AUTO_PERIOD`, `AUTO_SURFADA`, `SESSAO_ASIA_TOQUIO`, `SESSAO_LONDRES`, `SESSAO_NOVA_YORQUI` |
| **6. Posicionamento de Ordem** | `EXPANSAO_MINIMA`, `EXPANSAO_MAXIMA`, `compra`, `venda`, `santo`, `dedo`, `posicaoTake`, `buy_take`, `sell_take` |
| **7. Gestão de Capital & Risco** | `SALDO`, `lot` *(deve ser 0.00)*, `GERENCIAMENTO_DE_RISCO_DIARIO`, `porcentos`, `poercentosg`, `GERENCIAMENTO_DE_RISCO_SEMANAL`, `PORCENTOO`, `PORCENTOSS` |
| **8. Parâmetros Operacionais** | `ativar_ou_desativar_compra`, `ativar_ou_desativar_venda`, `Modify_Sl_For_OxO`, `condicao_De_rompimento_c`, `condicao_De_rompimento_v`, `GMAIL`, `notific` |
| **9. Resultado & Câmbio** | `CAMBIO` |

---

## 4. Formatação do Identificador Único (`id`)

O **Identificador Único (`id`)** distingue os templates no banco de dados.

### Regras de Formatação do `id`:
1. **Padrão de Nomenclatura**:
   Use `tpl_<codigo_tres_digitos>_<estrategia>_<timeframe_ou_perfil>` (ex: `tpl_001_fimathe_m15`, `tpl_002_surfada_d1`, `tpl_003_scalper_m1`).
2. **Consistência Chave-Propriedade**:
   O valor do `id` deve ser **EXATAMENTE IDÊNTICO** na chave do objeto JSON e na propriedade interna `"id"`.
   ```json
   "tpl_001_fimathe_m15": {
     "id": "tpl_001_fimathe_m15",
     ...
   }
   ```
3. **Restrições**: Apenas letras minúsculas, números e sublinhados (`_`).

---

## 5. Formatação do Título (`titulo`) e Descrição (`descricao`)

- **Título**: `⚡ Template M15 Gold Conservador (Oficial Admin)`
- **Descrição**: Deve conter propósito, paridades alvo, sessões de mercado e alerta de lote zerado (`0.00`) por segurança.

---

## 6. Regra de Publicação e Substituição Automática

- O aplicativo exibe **no máximo 3 templates ativos** simultaneamente.
- Quando a data `"validoAte"` expira (ex: `"31/12/2026"`), o template deixa de ser exibido e é **substituído automaticamente** pelo próximo template publicado no banco.

---

## 7. Exemplos de JSON Schema (Formatos Suportados)

O sistema do aplicativo aceita tanto a publicação de **Múltiplos Templates** quanto de **1 Template Único Individual**.

### FORMATO A: Publicação de Múltiplos Templates (Recomendado para até 3 Ativos)

```json
{
  "instrucoes_admin_templates": {
    "descricao": "Schema Único de Parâmetros para Publicação de Templates pelo Administrador Master.",
    "limite_publicados_ativos": 3,
    "regra_substituicao": "Publicação de 3 templates ativos. Quando 1 expira, é automaticamente substituído pelo próximo publicado.",
    "templates": {
      "tpl_001_fimathe_m15": {
        "id": "tpl_001_fimathe_m15",
        "titulo": "⚡ Template M15 Gold Conservador (Oficial Admin)",
        "descricao": "Setup oficial com gestão de risco ajustada para XAUUSD e EURUSD nas sessões de Londres e Nova Iorque. Lote zerado 0.00 por segurança.",
        "autor": "Admin Master Fimaster",
        "dataPublicacao": "02/08/2026 09:00",
        "validoAte": "31/12/2026",
        "disponivel": true,
        "versaoMinimaEa": "v3.2",
        "pontosAtivo": "250 pts",
        "paridade": "XAUUSD",
        "config": {
          "mt5AccountId": "TEMPLATE",
          "SENHA": "123456",
          "ESQUEMA_CORES_ENUM": "CYAN_NEON",
          "cor_de_canal": "#22D3EE",
          "cor_de_linhas": "#FF00E5",
          "corr_de_equador": "#FFFF00",
          "LINHAS_DE_EQUADOR": false,
          "TREND": "TENDENCIA_DE_ALTA",
          "M_equador_alta": 1.2500,
          "M_equador_baixa": 1.2400,
          "TEMA": false,
          "ESTRATÉGIA": "FIMATHE",
          "virada_de_jogo": false,
          "Nives": 1.0,
          "Costurar": true,
          "OperationalPeriod": "PERIOD_M15",
          "lot": 0.00,
          "EA_ATIVO": true,
          "EA_AUTO": false,
          "AUTO_PERIOD": "HORA_1",
          "AUTO_SURFADA": false,
          "SESSAO_ASIA_TOQUIO": false,
          "SESSAO_LONDRES": true,
          "SESSAO_NOVA_YORQUI": true,
          "EXPANSAO_MINIMA": 10,
          "EXPANSAO_MAXIMA": 30,
          "compra": 1.2550,
          "venda": 1.2500,
          "santo": 20.0,
          "dedo": 10,
          "posicaoTake": false,
          "buy_take": 0.0,
          "sell_take": 0.0,
          "SALDO": 1000.0,
          "GERENCIAMENTO_DE_RISCO_DIARIO": true,
          "porcentos": 1.0,
          "poercentosg": 1.5,
          "GERENCIAMENTO_DE_RISCO_SEMANAL": false,
          "PORCENTOO": 2.0,
          "PORCENTOSS": 2.0,
          "GMAIL": true,
          "notific": true,
          "ativar_ou_desativar_venda": true,
          "ativar_ou_desativar_compra": true,
          "Modify_Sl_For_OxO": true,
          "condicao_De_rompimento_c": true,
          "condicao_De_rompimento_v": true,
          "CAMBIO": 64.0
        }
      },
      "tpl_002_surfada_d1": {
        "id": "tpl_002_surfada_d1",
        "titulo": "🌊 Template Surfada D1 Agressivo (Oficial Admin)",
        "descricao": "Estratégia F_SURFADA no gráfico diário com virada de jogo ativada para grandes tendências em EURUSD. Lote zerado 0.00 por segurança.",
        "autor": "Admin Master Fimaster",
        "dataPublicacao": "01/08/2026 14:30",
        "validoAte": "31/12/2026",
        "disponivel": true,
        "versaoMinimaEa": "v3.2",
        "pontosAtivo": "500 pts",
        "paridade": "EURUSD",
        "config": {
          "mt5AccountId": "TEMPLATE",
          "SENHA": "123456",
          "ESQUEMA_CORES_ENUM": "CYAN_NEON",
          "cor_de_canal": "#22D3EE",
          "cor_de_linhas": "#FF00E5",
          "corr_de_equador": "#FFFF00",
          "LINHAS_DE_EQUADOR": false,
          "TREND": "TENDENCIA_DE_ALTA",
          "M_equador_alta": 1.2500,
          "M_equador_baixa": 1.2400,
          "TEMA": false,
          "ESTRATÉGIA": "F_SURFADA",
          "virada_de_jogo": true,
          "Nives": 2.0,
          "Costurar": true,
          "OperationalPeriod": "PERIOD_D1",
          "lot": 0.00,
          "EA_ATIVO": true,
          "EA_AUTO": true,
          "AUTO_PERIOD": "DIARIO",
          "AUTO_SURFADA": true,
          "SESSAO_ASIA_TOQUIO": false,
          "SESSAO_LONDRES": true,
          "SESSAO_NOVA_YORQUI": true,
          "EXPANSAO_MINIMA": 10,
          "EXPANSAO_MAXIMA": 30,
          "compra": 1.2550,
          "venda": 1.2500,
          "santo": 20.0,
          "dedo": 10,
          "posicaoTake": false,
          "buy_take": 0.0,
          "sell_take": 0.0,
          "SALDO": 1000.0,
          "GERENCIAMENTO_DE_RISCO_DIARIO": true,
          "porcentos": 1.0,
          "poercentosg": 1.5,
          "GERENCIAMENTO_DE_RISCO_SEMANAL": false,
          "PORCENTOO": 2.0,
          "PORCENTOSS": 2.0,
          "GMAIL": true,
          "notific": true,
          "ativar_ou_desativar_venda": true,
          "ativar_ou_desativar_compra": true,
          "Modify_Sl_For_OxO": true,
          "condicao_De_rompimento_c": true,
          "condicao_De_rompimento_v": true,
          "CAMBIO": 64.0
        }
      }
    }
  }
}
```

---

### FORMATO B: Publicação de 1 Único Template Individual (Single Template Schema)

Se o Administrador desejar publicar apenas **1 Único Template**, pode publicar o JSON no nó `/dados/instrucoes_admin_templates.json` exatamente assim:

```json
{
  "id": "tpl_001_fimathe_m15",
  "titulo": "⚡ Template M15 Gold Conservador (Oficial Admin)",
  "descricao": "Setup oficial com gestão de risco ajustada para XAUUSD e EURUSD nas sessões de Londres e Nova Iorque. Lote zerado 0.00 por segurança.",
  "autor": "Admin Master Fimaster",
  "dataPublicacao": "02/08/2026 09:00",
  "validoAte": "31/12/2026",
  "disponivel": true,
  "versaoMinimaEa": "v3.2",
  "pontosAtivo": "250 pts",
  "paridade": "XAUUSD",
  "config": {
    "mt5AccountId": "TEMPLATE",
    "SENHA": "123456",
    "ESQUEMA_CORES_ENUM": "CYAN_NEON",
    "cor_de_canal": "#22D3EE",
    "cor_de_linhas": "#FF00E5",
    "corr_de_equador": "#FFFF00",
    "LINHAS_DE_EQUADOR": false,
    "TREND": "TENDENCIA_DE_ALTA",
    "M_equador_alta": 1.2500,
    "M_equador_baixa": 1.2400,
    "TEMA": false,
    "ESTRATÉGIA": "FIMATHE",
    "virada_de_jogo": false,
    "Nives": 1.0,
    "Costurar": true,
    "OperationalPeriod": "PERIOD_M15",
    "lot": 0.00,
    "EA_ATIVO": true,
    "EA_AUTO": false,
    "AUTO_PERIOD": "HORA_1",
    "AUTO_SURFADA": false,
    "SESSAO_ASIA_TOQUIO": false,
    "SESSAO_LONDRES": true,
    "SESSAO_NOVA_YORQUI": true,
    "EXPANSAO_MINIMA": 10,
    "EXPANSAO_MAXIMA": 30,
    "compra": 1.2550,
    "venda": 1.2500,
    "santo": 20.0,
    "dedo": 10,
    "posicaoTake": false,
    "buy_take": 0.0,
    "sell_take": 0.0,
    "SALDO": 1000.0,
    "GERENCIAMENTO_DE_RISCO_DIARIO": true,
    "porcentos": 1.0,
    "poercentosg": 1.5,
    "GERENCIAMENTO_DE_RISCO_SEMANAL": false,
    "PORCENTOO": 2.0,
    "PORCENTOSS": 2.0,
    "GMAIL": true,
    "notific": true,
    "ativar_ou_desativar_venda": true,
    "ativar_ou_desativar_compra": true,
    "Modify_Sl_For_OxO": true,
    "condicao_De_rompimento_c": true,
    "condicao_De_rompimento_v": true,
    "CAMBIO": 64.0
  }
}
```
