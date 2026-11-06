# CRUD de Usuários (Java + JDBC) — Desenvolvimento para Servidores I (P2)

Back-end completo com **Create, Read, Update e Delete** de usuários, baseado no projeto
"Login" das Notas de Aula de 29/10 (cadastro → login → tela inicial → alterar dados → excluir conta).
Em vez das telas Swing, a camada de entrada é uma **CLI** (mesmo fluxo) e uma **API REST**;
as duas são adaptadores finos sobre a mesma camada de serviço.

## O que foi aproveitado das notas e o que foi mudado de propósito

| Nas notas de aula | Neste projeto |
|---|---|
| Tabela única `usuario` (nome, usuário, senha) | Mesma tabela, em SQL ANSI (`schema.sql`) + `id` e `criado_em` |
| Classe `Conexao` (JDBC) | `config/ConexaoFactory` (+ `AppConfig`: URL/usuário/senha fora do código) |
| Classe `Usuario` com `verificaUsuario(usuario, senha)` / `verificaUsuario(usuario)` (sobrecarga), `cadastraUsuario`, alteração e exclusão | `UsuarioDao` (acesso a dados) + `UsuarioService` (regras); `autenticar`, `existeLogin`, `cadastrar`, `alterar`, `excluir` |
| Validações: campos obrigatórios, senha = confirmar senha, usuário não duplicado | Mantidas em `UsuarioService` (mensagens equivalentes) |
| Senha com `MD5()` em `CHAR(32)` | **Substituído por PBKDF2-HMAC-SHA256 com salt** (`security/PasswordHasher`): MD5 é rápido e sem salt, portanto inadequado para senhas |
| MySQL + `mysql-connector-java` | H2 embarcado por padrão (zero instalação); MySQL suportado por configuração (ver abaixo) |
| Telas Swing (`TelaLogin`, `TelaCadastro`, `TelaInicio`, `TelaAlteracao`) | `cli/MenuConsole` com o mesmo fluxo + `web/ApiServer` (REST) |
| Atributos estáticos `nomeUsuario`/`usuarioSistema` para "lembrar" o logado | Sem estado estático: o usuário logado é passado como parâmetro (CLI) ou identificado a cada requisição (API, HTTP Basic) |

## Arquitetura em camadas

```
 CLI (MenuConsole)      API REST (ApiServer + Dtos)      ← entrada/saída
              \              /
               UsuarioService                            ← regras de negócio, validação, hash
                     |
                UsuarioDao (interface)                   ← contrato DAO
                     |
               UsuarioDaoJdbc                            ← JDBC + PreparedStatement
                     |
          ConexaoFactory → H2 / MySQL                    ← schema.sql, data.sql
```

```
src/main/java/com/dsi/usuarios/
├── Main.java                 ponto de entrada (cli | api)
├── Aplicacao.java            raiz de composição (monta config → DAO → serviço)
├── config/    AppConfig, ConexaoFactory, SchemaInitializer
├── model/     Usuario                                (entidade, getters/setters, construtores sobrecarregados)
├── dao/       UsuarioDao, UsuarioDaoJdbc
├── service/   UsuarioService
├── security/  PasswordHasher
├── exception/ NegocioException e filhas (Validacao, UsuarioDuplicado, UsuarioNaoEncontrado,
│              Autenticacao, AcessoNegado) + PersistenciaException (infraestrutura)
├── cli/       MenuConsole
└── web/       ApiServer, Dtos
src/main/resources/  schema.sql, data.sql, schema-mysql.sql, application.properties
src/test/java/       37 testes (JUnit 5) — DAO, serviço, hash, schema, CLI e API ponta a ponta
```

**Requisitos de qualidade atendidos**
- *SQL Injection:* todo valor entra por `?` em `PreparedStatement` (nenhuma concatenação de SQL); há testes com payloads (`' OR '1'='1`, `'; DROP TABLE...`) no DAO, no serviço e via HTTP.
- *Recursos:* `Connection`, `PreparedStatement` e `ResultSet` sempre em `try-with-resources`.
- *Exceções:* `SQLException` é traduzida para `PersistenciaException` (detalhe vai só para o log; o cliente da API recebe mensagem genérica) e a violação de chave única vira `UsuarioDuplicadoException`. Falhas de regra viram `NegocioException` com status HTTP próprio (400/401/403/404/409).
- *Autenticação:* mensagem única "Usuário ou senha inválidos" para login inexistente ou senha errada, e o custo do PBKDF2 é pago nos dois casos.
- *Autorização:* na API só o próprio usuário altera/exclui a própria conta (403 caso contrário).

## Pré-requisitos

- **JDK 17 ou superior** (testado com JDK 26). Defina `JAVA_HOME`.
- **Maven não precisa estar instalado**: use o wrapper (`mvnw.cmd` no Windows, `./mvnw` no Linux/macOS).
  No IntelliJ: *File > Open* na pasta do projeto → ele reconhece o `pom.xml`.
- Primeira execução baixa dependências do Maven Central (internet necessária).

## Compilar e testar

```powershell
# PowerShell (Windows), na pasta do projeto
$env:JAVA_HOME = "C:\caminho\do\jdk"
.\mvnw.cmd test          # compila e roda os 37 testes
.\mvnw.cmd package       # testes + jar executável em target\crud-usuarios.jar
```

```bash
# Linux/macOS/Git Bash
./mvnw test
./mvnw package
```

## Executar

O banco H2 é criado em `./data/usuarios.mv.db` (relativo à pasta onde você roda o comando) na
primeira execução, junto com duas contas de demonstração (`data.sql`):
`admin` / `Admin@123` e `aluno` / `Aluno@123` (apague-as em qualquer uso real).

### CLI (fluxo das aulas)

```powershell
java -jar target\crud-usuarios.jar cli
```

`1 Entrar` / `2 Cadastrar` → após o login: `1 Alterar meus dados`, `2 Excluir minha conta`,
`3 Listar usuários`. Como nas aulas, depois de alterar ou excluir você volta à tela de login.
Na alteração, Enter mantém o valor atual (inclusive a senha).

### API REST

```powershell
java -jar target\crud-usuarios.jar api       # http://127.0.0.1:8090
```

Mudar porta/host: `java -Dserver.port=9000 -jar ...` ou variável `SERVER_PORT`.

| Método | Rota | Autenticação | Sucesso | Erros |
|---|---|---|---|---|
| POST | `/usuarios` | — | 201 + `Location` | 400 validação, 409 login já existe |
| POST | `/login` | — (credenciais no corpo) | 200 | 401 |
| GET | `/usuarios` | HTTP Basic | 200 (lista) | 401 |
| GET | `/usuarios/{id}` | HTTP Basic | 200 | 401, 404 |
| PUT | `/usuarios/{id}` | HTTP Basic, só o próprio | 200 | 400, 401, 403, 404, 409 |
| DELETE | `/usuarios/{id}` | HTTP Basic, só o próprio | 204 | 401, 403, 404 |

Corpos JSON — cadastro: `{"nome","usuario","senha","confirmacaoSenha"}`;
alteração: `{"nome","usuario"}` + opcionalmente `{"senha","confirmacaoSenha"}`; login: `{"usuario","senha"}`.
Erros: `{"status":409,"erro":"Já existe um usuário com o login 'joao'."}`.
Regras: nome obrigatório (≤ 100); usuário 3–50 caracteres `a-z 0-9 . _ -` (gravado em minúsculas);
senha 8–128 caracteres e igual à confirmação.

**Exemplos com curl** (Git Bash, Linux, macOS; use `curl.exe` no PowerShell só com arquivos `--data-binary @arq.json`,
pois o PowerShell 5.1 remove aspas de argumentos):

```bash
B=http://127.0.0.1:8090
# CREATE
curl -i -X POST $B/usuarios -H 'Content-Type: application/json' \
  -d '{"nome":"Joao da Silva","usuario":"joao","senha":"Senha@123","confirmacaoSenha":"Senha@123"}'
# LOGIN
curl -X POST $B/login -H 'Content-Type: application/json' -d '{"usuario":"joao","senha":"Senha@123"}'
# READ
curl -u 'joao:Senha@123' $B/usuarios
curl -u 'joao:Senha@123' $B/usuarios/3
# UPDATE (id retornado no cadastro)
curl -u 'joao:Senha@123' -X PUT $B/usuarios/3 -H 'Content-Type: application/json' \
  -d '{"nome":"Joao S. Silva","usuario":"joao"}'
# DELETE
curl -i -u 'joao:Senha@123' -X DELETE $B/usuarios/3
```

**Exemplo em PowerShell** (sem curl):

```powershell
$B = "http://127.0.0.1:8090"
$h = @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("joao:Senha@123")) }
$novo = @{ nome="Joao da Silva"; usuario="joao"; senha="Senha@123"; confirmacaoSenha="Senha@123" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$B/usuarios" -ContentType "application/json" -Body $novo
Invoke-RestMethod -Uri "$B/usuarios" -Headers $h
```

> O corpo da requisição precisa estar em **UTF-8**. Em terminais Windows que enviam outra codificação,
> nomes com acento chegam inválidos e a API responde `400 JSON inválido` — envie de um arquivo UTF-8.

## Configuração e MySQL

Chaves em `application.properties`; qualquer uma pode ser sobrescrita por `-Dchave=valor` ou por variável de
ambiente (`db.url` → `DB_URL`). Não grave senhas de banco no arquivo.

Para usar o **MySQL das aulas** (variante **não executada contra um MySQL real** neste desenvolvimento — o
código é JDBC padrão, e a única diferença de SQL é a coluna de identidade):

```powershell
# 1) no MySQL:  CREATE DATABASE dsi_usuarios CHARACTER SET utf8mb4;
# 2) empacotar com o driver:
.\mvnw.cmd -Pmysql package
# 3) executar
$env:DB_URL = "jdbc:mysql://localhost:3306/dsi_usuarios?useSSL=false&serverTimezone=UTC"
$env:DB_USER = "root"; $env:DB_PASSWORD = "sua-senha"; $env:DB_SCHEMA = "schema-mysql.sql"
java -jar target\crud-usuarios.jar cli
```

## Limitações conhecidas

- A API usa **HTTP Basic sobre HTTP puro** e escuta só em `127.0.0.1`. Para expor na rede, coloque atrás de HTTPS (proxy reverso). Não há tokens/sessões nem limite de tentativas de login.
- Uma conexão JDBC é aberta por operação (sem pool) — suficiente para fins didáticos; em produção use HikariCP.
- O `SchemaInitializer` só cria o schema na primeira execução; não há migrações (Flyway/Liquibase seria o próximo passo).
- `data.sql` é dividido por `;` — não use `;` dentro de textos literais nele.
- O hash PBKDF2 usa 600.000 iterações (`security.pbkdf2.iterations`); cada login leva centenas de milissegundos de propósito.

## Entrega (GitHub)

Como na P1: crie um repositório vazio no GitHub, depois, na pasta `crud-usuarios-java`:

```bash
git init && git add . && git commit -m "CRUD de usuarios em Java (P2)"
git branch -M main && git remote add origin <URL_DO_SEU_REPOSITORIO> && git push -u origin main
```
