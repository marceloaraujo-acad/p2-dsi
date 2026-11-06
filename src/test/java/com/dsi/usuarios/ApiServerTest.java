package com.dsi.usuarios;

import com.dsi.usuarios.web.ApiServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Teste de integracao ponta a ponta: HTTP -> API -> servico -> DAO -> H2. */
class ApiServerTest {

    private static final String SENHA = "Senha@123";

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private ApiServer api;
    private String base;

    @BeforeEach
    void subir() throws Exception {
        api = new ApiServer(TestSupport.aplicacao().service(), "127.0.0.1", 0);
        api.iniciar();
        base = "http://127.0.0.1:" + api.porta();
    }

    @AfterEach
    void descer() {
        api.close();
    }

    @Test
    void fluxoCrudCompleto() throws Exception {
        // CREATE
        HttpResponse<String> criado = enviar("POST", "/usuarios", null,
                "{\"nome\":\"Ana Silva\",\"usuario\":\"ana\",\"senha\":\"" + SENHA + "\",\"confirmacaoSenha\":\"" + SENHA + "\"}");
        assertEquals(201, criado.statusCode());
        JsonNode corpo = json.readTree(criado.body());
        long id = corpo.get("id").asLong();
        assertEquals("/usuarios/" + id, criado.headers().firstValue("Location").orElseThrow());
        assertEquals("Ana Silva", corpo.get("nome").asText());
        assertNotNull(corpo.get("criadoEm"));
        assertFalse(criado.body().contains("senha"), "a resposta nunca deve expor senha/hash");

        // LOGIN
        HttpResponse<String> login = enviar("POST", "/login", null, "{\"usuario\":\"ana\",\"senha\":\"" + SENHA + "\"}");
        assertEquals(200, login.statusCode());
        assertEquals(id, json.readTree(login.body()).get("id").asLong());

        // READ (um e lista)
        String auth = basic("ana", SENHA);
        HttpResponse<String> um = enviar("GET", "/usuarios/" + id, auth, null);
        assertEquals(200, um.statusCode());
        assertEquals("ana", json.readTree(um.body()).get("usuario").asText());
        HttpResponse<String> lista = enviar("GET", "/usuarios", auth, null);
        assertEquals(200, lista.statusCode());
        assertEquals(1, json.readTree(lista.body()).size());

        // UPDATE (nome + nova senha)
        HttpResponse<String> alterado = enviar("PUT", "/usuarios/" + id, auth,
                "{\"nome\":\"Ana Maria\",\"usuario\":\"ana\",\"senha\":\"Nova@12345\",\"confirmacaoSenha\":\"Nova@12345\"}");
        assertEquals(200, alterado.statusCode());
        assertEquals("Ana Maria", json.readTree(alterado.body()).get("nome").asText());
        assertEquals(401, enviar("GET", "/usuarios/" + id, auth, null).statusCode()); // senha antiga morreu
        String novaAuth = basic("ana", "Nova@12345");
        assertEquals(200, enviar("GET", "/usuarios/" + id, novaAuth, null).statusCode());

        // DELETE
        assertEquals(204, enviar("DELETE", "/usuarios/" + id, novaAuth, null).statusCode());
        assertEquals(401, enviar("GET", "/usuarios/" + id, novaAuth, null).statusCode()); // nao existe mais
        assertEquals(401, enviar("POST", "/login", null, "{\"usuario\":\"ana\",\"senha\":\"Nova@12345\"}").statusCode());
    }

    @Test
    void validacaoEDuplicidade() throws Exception {
        assertEquals(400, enviar("POST", "/usuarios", null, "{\"nome\":\"\",\"usuario\":\"ana\",\"senha\":\"" + SENHA + "\",\"confirmacaoSenha\":\"" + SENHA + "\"}").statusCode());
        assertEquals(400, enviar("POST", "/usuarios", null, "{\"nome\":\"Ana\",\"usuario\":\"ana\",\"senha\":\"" + SENHA + "\",\"confirmacaoSenha\":\"outra\"}").statusCode());
        assertEquals(400, enviar("POST", "/usuarios", null, "isto nao e json").statusCode());
        assertEquals(400, enviar("POST", "/usuarios", null, "").statusCode());

        String ok = "{\"nome\":\"Ana\",\"usuario\":\"ana\",\"senha\":\"" + SENHA + "\",\"confirmacaoSenha\":\"" + SENHA + "\"}";
        assertEquals(201, enviar("POST", "/usuarios", null, ok).statusCode());
        HttpResponse<String> dup = enviar("POST", "/usuarios", null, ok);
        assertEquals(409, dup.statusCode());
        assertTrue(json.readTree(dup.body()).get("erro").asText().contains("ana"));
    }

    @Test
    void autenticacaoEAutorizacao() throws Exception {
        long ana = criar("ana");
        long beto = criar("beto");

        // sem credencial / credencial errada / formato errado
        HttpResponse<String> semAuth = enviar("GET", "/usuarios", null, null);
        assertEquals(401, semAuth.statusCode());
        assertTrue(semAuth.headers().firstValue("WWW-Authenticate").orElse("").startsWith("Basic"));
        assertEquals(401, enviar("GET", "/usuarios", basic("ana", "errada123"), null).statusCode());
        assertEquals(401, enviar("GET", "/usuarios", "Basic %%%nao-e-base64", null).statusCode());
        assertEquals(401, enviar("GET", "/usuarios", "Bearer abc", null).statusCode());

        // ana nao pode alterar nem excluir beto (403), mas ainda pode le-lo
        String authAna = basic("ana", SENHA);
        assertEquals(403, enviar("PUT", "/usuarios/" + beto, authAna,
                "{\"nome\":\"Hack\",\"usuario\":\"beto\"}").statusCode());
        assertEquals(403, enviar("DELETE", "/usuarios/" + beto, authAna, null).statusCode());
        assertEquals(200, enviar("GET", "/usuarios/" + beto, authAna, null).statusCode());

        // alterar para login ja usado => 409
        assertEquals(409, enviar("PUT", "/usuarios/" + ana, authAna,
                "{\"nome\":\"Ana\",\"usuario\":\"beto\"}").statusCode());
    }

    @Test
    void sqlInjectionViaHttpNaoAutentica() throws Exception {
        criar("ana");
        assertEquals(401, enviar("POST", "/login", null,
                "{\"usuario\":\"' OR '1'='1' --\",\"senha\":\"x\"}").statusCode());
        assertEquals(401, enviar("GET", "/usuarios", basic("ana' OR '1'='1", "x"), null).statusCode());
    }

    @Test
    void rotasEMetodosInvalidos() throws Exception {
        assertEquals(404, enviar("GET", "/nada", null, null).statusCode());
        assertEquals(405, enviar("PATCH", "/usuarios", null, null).statusCode());
        assertEquals(405, enviar("GET", "/login", null, null).statusCode());
        criar("ana");
        assertEquals(400, enviar("GET", "/usuarios/abc", basic("ana", SENHA), null).statusCode());
        assertEquals(404, enviar("GET", "/usuarios/9999", basic("ana", SENHA), null).statusCode());
    }

    @Test
    void corpoGrandeDemaisERejeitado() throws Exception {
        String grande = "{\"nome\":\"" + "x".repeat(20_000) + "\"}";
        assertEquals(400, enviar("POST", "/usuarios", null, grande).statusCode());
    }

    // ------------------------------------------------------------------ helpers

    private long criar(String login) throws Exception {
        HttpResponse<String> r = enviar("POST", "/usuarios", null,
                "{\"nome\":\"" + login + "\",\"usuario\":\"" + login + "\",\"senha\":\"" + SENHA + "\",\"confirmacaoSenha\":\"" + SENHA + "\"}");
        assertEquals(201, r.statusCode());
        return json.readTree(r.body()).get("id").asLong();
    }

    private static String basic(String usuario, String senha) {
        return "Basic " + Base64.getEncoder().encodeToString((usuario + ":" + senha).getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> enviar(String metodo, String caminho, String authorization, String corpo) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + caminho))
                .header("Content-Type", "application/json");
        if (authorization != null) {
            b.header("Authorization", authorization);
        }
        b.method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
