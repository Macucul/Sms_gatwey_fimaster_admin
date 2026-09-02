//+------------------------------------------------------------------+
//|                                              ea_mql5_login.mq5   |
//|                                             FiMaster Tech Theme  |
//|                                        https://fimaster-tech.com |
//+------------------------------------------------------------------+
#property copyright "FiMaster Tech"
#property link      "https://fimaster-tech.com"
#property version   "1.00"
#property strict

//+------------------------------------------------------------------+
//| Parâmetros de Entrada do Robô (Inputs)                          |
//+------------------------------------------------------------------+
input group "=== CONFIGURAÇÃO DO SERVIDOR ==="
enum ENUM_SYNC_MODE {
   MODE_FASTAPI = 0,   // FastAPI / Servidor Web Próprio
   MODE_FIREBASE = 1,  // Firebase Realtime Database (REST API)
   MODE_GITHUB = 2     // GitHub Repository (Raw Files)
};

input ENUM_SYNC_MODE InpSyncMode = MODE_FASTAPI; // Modo de Sincronização
input string InpServerUrl = "https://seu-servidor-fastapi.com"; // URL do Servidor / Firebase / Base GitHub
input string InpGitHubRepo = "usuario/repositorio"; // GitHub: Dono/Repositorio (Ex: admin/my-repo)
input string InpGitHubBranch = "main"; // GitHub: Nome da Branch (Ex: main ou master)
input string InpGitHubToken = ""; // GitHub: Token de Acesso Pessoal (Opcional se repositório público)

input group "=== CREDENCIAIS DE AUTENTICAÇÃO ==="
input string InpMt5Id = ""; // ID da Conta MT5 (Deixe vazio para usar o número atual da conta)
input string InpEaPassword = ""; // Senha de Ativação do EA

//+------------------------------------------------------------------+
//| Função de Inicialização do Expert Advisor                       |
//+------------------------------------------------------------------+
int OnInit()
{
   Print("▶️ [FIMASTER] Iniciando sistema de autenticação segura...");

   // Se o ID da conta estiver vazio, usa a conta corrente ativa no terminal
   string mt5Id = InpMt5Id;
   if(StringLen(mt5Id) == 0) {
      mt5Id = IntegerToString(AccountNumber());
   }
   
   if(StringLen(InpEaPassword) == 0) {
      Print("❌ [FALHA] Por favor, insira a senha de ativação do EA nos parâmetros!");
      return(INIT_FAILED);
   }

   // Executa o fluxo de autenticação sequencial
   bool autenticado = ExecutarAutenticacaoMql5(mt5Id, InpEaPassword);
   
   if(autenticado) {
      Print("🎉 [SUCESSO] Robô autenticado e licenciado com sucesso para a conta MT5: ", mt5Id);
      // O seu código do Robô (inicialização de indicadores, painéis, etc.) inicia aqui!
      return(INIT_SUCCEEDED);
   } else {
      Print("❌ [BLOQUEADO] Licença Inválida ou Falha na Autenticação para a conta MT5: ", mt5Id);
      Alert("FiMaster EA: Autenticação Falhou! Verifique os logs e as suas credenciais.");
      return(INIT_FAILED);
   }
}

//+------------------------------------------------------------------+
//| Função principal que executa o fluxo sequencial de login         |
//+------------------------------------------------------------------+
bool ExecutarAutenticacaoMql5(string mt5Id, string passwordSent)
{
   Print("🔍 [PASSO 1] Buscando conta MT5 '", mt5Id, "' no índice remoto...");
   
   string pathIndex = "dados/indices/mt5.json";
   string indexJson = BuscarConteudoRemoto(pathIndex);
   
   if(StringLen(indexJson) == 0) {
      Print("❌ [FALHA] Não foi possível obter o índice de contas MT5 do servidor.");
      return false;
   }
   
   // Extrai o ID do utilizador correspondente ao mt5Id a partir do JSON do índice
   string userId = ExtrairValorJson(indexJson, mt5Id, "usuario");
   if(StringLen(userId) == 0) {
      Print("❌ [FALHA] Conta MT5 '", mt5Id, "' não encontrada no índice remoto.");
      return false;
   }
   
   Print("✅ [ÍNDICE] Conta MT5 localizada. Associada ao Utilizador ID: '", userId, "'");
   Print("🔍 [PASSO 2] Carregando dados cadastrais do utilizador remoto...");
   
   string pathUser = "dados/usuarios/" + userId + ".json";
   string userJson = BuscarConteudoRemoto(pathUser);
   
   if(StringLen(userJson) == 0) {
      Print("❌ [FALHA] Não foi possível ler o registro do utilizador '", userId, "' no servidor.");
      return false;
   }
   
   // Extrai os campos de segurança: hash da senha e o sal (salt)
   // PADRÃO: chave JSON em Firebase é "senha_hash"
   string senhaHashCompleta = ExtrairValorJson(userJson, "", "senha_hash");
   string salt = ExtrairValorJson(userJson, "", "salt");
   string validadeLicenca = ExtrairValorJson(userJson, "", "validadeLicenca");
   string statusLicenca = ExtrairValorJson(userJson, "", "statusLicenca");
   
   if(StringLen(senhaHashCompleta) == 0) {
      Print("❌ [FALHA] Registro do utilizador está incompleto ou corrompido.");
      return false;
   }
   
   // Extrai apenas a primeira parte do hash (antes de ":") caso venha no formato "hash:salt"
   string hashEsperado = senhaHashCompleta;
   int colonPos = StringFind(senhaHashCompleta, ":");
   if(colonPos >= 0) {
      hashEsperado = StringSubstr(senhaHashCompleta, 0, colonPos);
   }
   
   // Se o sal do banco estiver vazio, tenta extrair a segunda parte do hash de senha_hash
   if(StringLen(salt) == 0 && colonPos >= 0) {
      salt = StringSubstr(senhaHashCompleta, colonPos + 1);
   }

   Print("📊 [DADOS] Validade da licença: ", validadeLicenca, " | Status: ", statusLicenca);
   
   // Verifica se a licença expirou ou está inativa
   if(statusLicenca == "EXPIRADO" || statusLicenca == "INATIVO") {
      Print("❌ [BLOQUEADO] Licença com status inválido: '", statusLicenca, "'");
      return false;
   }

   Print("🔐 [PASSO 3] Calculando assinatura digital com SHA-256...");
   
   // Realiza a criptografia com o salt igual ao gerado no Android (senha + salt)
   string hashGerado = CalcularHashSha256(passwordSent, salt);
   
   Print("ℹ️ [CONSOLA] Salt extraído: '", salt, "'");
   Print("🔑 [CONSOLA] Hash Gerado:   '", hashGerado, "'");
   Print("🔒 [CONSOLA] Hash Esperado: '", hashEsperado, "'");
   
   if(hashGerado == hashEsperado) {
      Print("✅ [CONEXÃO] Assinatura verificada! Acesso autorizado.");
      return true;
   }
   
   Print("❌ [FALHA] Senha de ativação incorreta para a conta MT5.");
   return false;
}

//+------------------------------------------------------------------+
//| Função para buscar dados remotos através de requisição HTTP      |
//+------------------------------------------------------------------+
string BuscarConteudoRemoto(string path)
{
   string url = "";
   string headers = "User-Agent: MetaTrader 5\r\n";
   char post[], result[];
   string result_headers;
   int timeout = 5000; // 5 segundos de timeout
   
   // Constrói a URL final de acordo com o modo de sincronização selecionado
   if(InpSyncMode == MODE_FASTAPI) {
      url = InpServerUrl;
      // Remove barra final se houver
      if(StringSubstr(url, StringLen(url)-1, 1) == "/") {
         url = StringSubstr(url, 0, StringLen(url)-1);
      }
      url = url + "/" + path;
   } 
   else if(InpSyncMode == MODE_FIREBASE) {
      url = InpServerUrl;
      if(StringSubstr(url, StringLen(url)-1, 1) == "/") {
         url = StringSubstr(url, 0, StringLen(url)-1);
      }
      // O Firebase Realtime Database expõe JSON diretamente adicionando .json no fim
      url = url + "/" + path;
   } 
   else if(InpSyncMode == MODE_GITHUB) {
      url = "https://raw.githubusercontent.com/" + InpGitHubRepo + "/" + InpGitHubBranch + "/" + path;
      if(StringLen(InpGitHubToken) > 0) {
         headers += "Authorization: token " + InpGitHubToken + "\r\n";
      }
   }
   
   ResetLastError();
   // Executa o WebRequest do MetaTrader 5
   // ATENÇÃO: Adicione a URL base no menu do MT5: Ferramentas -> Opções -> Experts -> Permitir WebRequest para as URLs listadas
   int res = WebRequest("GET", url, headers, timeout, post, result, result_headers);
   
   if(res == -1) {
      Print("❌ [ERRO HTTP] Falha no WebRequest para a URL: ", url);
      Print("👉 Verifique se adicionou esta URL em: Ferramentas -> Opções -> Experts -> Permitir WebRequest");
      Print("Erro código do terminal: ", GetLastError());
      return "";
   }
   
   if(res >= 200 && res < 300) {
      // Transforma o array de bytes em string UTF-8
      string responseStr = CharArrayToString(result, 0, WHOLE_ARRAY, CP_UTF8);
      return responseStr;
   }
   
   Print("❌ [ERRO HTTP] Código de resposta inválido: ", res, " para a URL: ", url);
   return "";
}

//+------------------------------------------------------------------+
//| Função auxiliar para extrair valores de chaves JSON simples      |
//+------------------------------------------------------------------+
string ExtrairValorJson(string json, string chavePai, string chaveDesejada)
{
   string jsonTratado = json;
   
   // Se o índice for aninhado (Ex: mt5.json onde a chave pai é o número da conta)
   if(StringLen(chavePai) > 0) {
      int posPai = StringFind(json, "\"" + chavePai + "\"");
      if(posPai < 0) return "";
      
      // Delimita a busca apenas para o bloco da chave pai
      int blocoInicio = StringFind(json, "{", posPai);
      int blocoFim = StringFind(json, "}", blocoInicio);
      if(blocoInicio >= 0 && blocoFim > blocoInicio) {
         jsonTratado = StringSubstr(json, blocoInicio, blocoFim - blocoInicio + 1);
      }
   }
   
   // Busca a chave desejada no formato: "chave":"valor" ou "chave":valor ou "chave" : "valor"
   string busca = "\"" + chaveDesejada + "\"";
   int posChave = StringFind(jsonTratado, busca);
   if(posChave < 0) return "";
   
   int posDoisPontos = StringFind(jsonTratado, ":", posChave + StringLen(busca));
   if(posDoisPontos < 0) return "";
   
   // Encontra o valor (seja ele string entre aspas ou numérico/booleano)
   int posInicioValor = posDoisPontos + 1;
   while(posInicioValor < StringLen(jsonTratado)) {
      string charAt = StringSubstr(jsonTratado, posInicioValor, 1);
      if(charAt != " " && charAt != "\t" && charAt != "\r" && charAt != "\n") {
         break;
      }
      posInicioValor++;
   }
   
   string primeiroChar = StringSubstr(jsonTratado, posInicioValor, 1);
   string resultado = "";
   
   if(primeiroChar == "\"") {
      // É uma string entre aspas duplas, lê até fechar aspas
      int posFimAspas = StringFind(jsonTratado, "\"", posInicioValor + 1);
      if(posFimAspas > posInicioValor) {
         resultado = StringSubstr(jsonTratado, posInicioValor + 1, posFimAspas - posInicioValor - 1);
      }
   } else {
      // É um valor numérico ou booleano, lê até uma vírgula ou fecho de chave ou quebra de linha
      int i = posInicioValor;
      while(i < StringLen(jsonTratado)) {
         string charAt = StringSubstr(jsonTratado, i, 1);
         if(charAt == "," || charAt == "}" || charAt == "]" || charAt == "\r" || charAt == "\n") {
            break;
         }
         resultado += charAt;
         i++;
      }
      resultado = StringTrim(resultado);
   }
   
   return resultado;
}

//+------------------------------------------------------------------+
//| Função que calcula o Hash SHA-256 equivalente ao Java/Kotlin      |
//+------------------------------------------------------------------+
string CalcularHashSha256(string password, string salt)
{
   string saltedInput = password + salt;
   uchar data[];
   uchar key[];
   uchar result[];
   
   StringToCharArray(saltedInput, data, 0, StringLen(saltedInput));
   
   // Codifica o hash com algoritmo SHA-256 nativo do MetaTrader 5
   int res = CryptEncode(CRYPT_HASH_SHA256, data, key, result);
   if(res <= 0) {
      Print("❌ [ERRO] Falha ao codificar Hash SHA-256!");
      return "";
   }
   
   // Converte os bytes em formato hexadecimal legível
   string hex = "";
   for(int i = 0; i < ArraySize(result); i++) {
      hex += StringFormat("%02x", result[i]);
   }
   
   return hex;
}

//+------------------------------------------------------------------+
//| Função Auxiliar para remover espaços adicionais                  |
//+------------------------------------------------------------------+
string StringTrim(string text)
{
   string t = text;
   while(StringLen(t) > 0 && (StringSubstr(t, 0, 1) == " " || StringSubstr(t, 0, 1) == "\t")) {
      t = StringSubstr(t, 1);
   }
   while(StringLen(t) > 0 && (StringSubstr(t, StringLen(t)-1, 1) == " " || StringSubstr(t, StringLen(t)-1, 1) == "\t")) {
      t = StringSubstr(t, 0, StringLen(t)-1);
   }
   return t;
}

//+------------------------------------------------------------------+
//| Fim do Script                                                   |
//+------------------------------------------------------------------+
