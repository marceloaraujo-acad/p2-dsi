package com.dsi.usuarios.web;

import com.dsi.usuarios.exception.AcessoNegadoException;
import com.dsi.usuarios.exception.AutenticacaoException;
import com.dsi.usuarios.exception.NegocioException;
import com.dsi.usuarios.exception.PersistenciaException;
import com.dsi.usuarios.exception.UsuarioDuplicadoException;
import com.dsi.usuarios.exception.UsuarioNaoEncontradoException;
import com.dsi.usuarios.exception.ValidacaoException;
import com.dsi.usuarios.model.Usuario;
import com.dsi.usuarios.service.UsuarioService;
import com.dsi.usuarios.web.Dtos.AlteracaoRequest;
import com.dsi.usuarios.web.Dtos.CadastroRequest;
import com.dsi.usuarios.web.Dtos.ErroResponse;
import com.dsi.usuarios.web.Dtos.LoginRequest;
import com.dsi.usuarios.web.Dtos.UsuarioResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Camada de controle HTTP/REST sobre o {@link UsuarioService}, usando apenas o servidor
 * embutido do JDK ({@code com.sun.net.httpserver}).
 *
 * <pre>
 *   POST   /usuarios        cadastro (publico)
 *   POST   /login           valida credenciais (publico)
 *   GET    /usuarios        lista            (Basic Auth)
 *   GET    /usuarios/{id}   consulta         (Basic Auth)
 *   PUT    /usuarios/{id}   altera           (Basic Auth, somente o proprio usuario)
 *   DELETE /usuarios/{id}   exclui           (Basic Auth, somente o proprio usuario)
 * </pre>
 */
public final class ApiServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ApiServer.class.getName());
    private static final int TAMANHO_MAX_CORPO = 16 * 1024;

    private final UsuarioService service;
    private final ObjectMapper json = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final HttpServer servidor;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    /** @param porta 0 escolhe uma porta livre (util em testes); veja {@link #porta()}. */
    public ApiServer(UsuarioService service, String host, int porta) throws IOException {
        this.service = service;
        this.servidor = HttpServer.create(new InetSocketAddress(host, porta), 0);
        this.servidor.createContext("/", this::tratar);
        this.servidor.setExecutor(executor);
    }

    public void iniciar() {
        servidor.start();
    }

    public int porta() {
        return servidor.getAddress().getPort();
    }

    @Override
    public void close() {
        servidor.stop(0);
        executor.shutdownNow();
    }

    // ------------------------------------------------------------------ roteamento

    private void tratar(HttpExchange ex) throws IOException {
        try {
            rotear(ex);
        } catch (NegocioException e) {
            responderErro(ex, e);
        } catch (PersistenciaException e) {
            LOG.log(Level.SEVERE, "Falha de persistência", e);
            enviar(ex, 500, new ErroResponse(500, "Erro interno ao acessar o banco de dados."));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Erro inesperado", e);
            enviar(ex, 500, new ErroResponse(500, "Erro interno."));
        } finally {
            ex.close();
        }
    }

    private void rotear(HttpExchange ex) throws IOException {
        String caminho = ex.getRequestURI().getPath();
        if (caminho.length() > 1 && caminho.endsWith("/")) {
            caminho = caminho.substring(0, caminho.length() - 1);
        }
        String metodo = ex.getRequestMethod();

        if (caminho.equals("/login")) {
            if ("POST".equals(metodo)) {
                login(ex);
            } else {
                metodoNaoPermitido(ex, "POST");
            }
        } else if (caminho.equals("/usuarios")) {
            switch (metodo) {
                case "POST" -> cadastrar(ex);
                case "GET" -> listar(ex);
                default -> metodoNaoPermitido(ex, "GET, POST");
            }
        } else if (caminho.startsWith("/usuarios/")) {
            long id = lerId(caminho.substring("/usuarios/".length()));
            switch (metodo) {
                case "GET" -> consultar(ex, id);
                case "PUT" -> alterar(ex, id);
                case "DELETE" -> excluir(ex, id);
                default -> metodoNaoPermitido(ex, "GET, PUT, DELETE");
            }
        } else {
            enviar(ex, 404, new ErroResponse(404, "Rota não encontrada."));
        }
    }

    // ------------------------------------------------------------------ handlers

    private void login(HttpExchange ex) throws IOException {
        LoginRequest req = lerCorpo(ex, LoginRequest.class);
        Usuario u = service.autenticar(req.usuario(), req.senha());
        enviar(ex, 200, UsuarioResponse.de(u));
    }

    private void cadastrar(HttpExchange ex) throws IOException {
        CadastroRequest req = lerCorpo(ex, CadastroRequest.class);
        Usuario criado = service.cadastrar(req.nome(), req.usuario(), req.senha(), req.confirmacaoSenha());
        ex.getResponseHeaders().set("Location", "/usuarios/" + criado.getId());
        enviar(ex, 201, UsuarioResponse.de(criado));
    }

    private void listar(HttpExchange ex) throws IOException {
        autenticar(ex);
        List<UsuarioResponse> lista = service.listar().stream().map(UsuarioResponse::de).toList();
        enviar(ex, 200, lista);
    }

    private void consultar(HttpExchange ex, long id) throws IOException {
        autenticar(ex);
        enviar(ex, 200, UsuarioResponse.de(service.buscarPorId(id)));
    }

    private void alterar(HttpExchange ex, long id) throws IOException {
        Usuario logado = autenticar(ex);
        exigirProprio(logado, id);
        AlteracaoRequest req = lerCorpo(ex, AlteracaoRequest.class);
        Usuario atualizado = service.alterar(id, req.nome(), req.usuario(), req.senha(), req.confirmacaoSenha());
        enviar(ex, 200, UsuarioResponse.de(atualizado));
    }

    private void excluir(HttpExchange ex, long id) throws IOException {
        Usuario logado = autenticar(ex);
        exigirProprio(logado, id);
        service.excluir(id);
        enviar(ex, 204, null);
    }

    // ------------------------------------------------------------------ seguranca

    /** HTTP Basic: "Authorization: Basic base64(usuario:senha)". */
    private Usuario autenticar(HttpExchange ex) {
        String cabecalho = ex.getRequestHeaders().getFirst("Authorization");
        if (cabecalho == null || !cabecalho.regionMatches(true, 0, "Basic ", 0, 6)) {
            throw new AutenticacaoException("Autenticação necessária (HTTP Basic).");
        }
        String credenciais;
        try {
            credenciais = new String(Base64.getDecoder().decode(cabecalho.substring(6).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new AutenticacaoException("Cabeçalho Authorization malformado.");
        }
        int separador = credenciais.indexOf(':');
        if (separador < 0) {
            throw new AutenticacaoException("Cabeçalho Authorization malformado.");
        }
        return service.autenticar(credenciais.substring(0, separador), credenciais.substring(separador + 1));
    }

    private static void exigirProprio(Usuario logado, long id) {
        if (!logado.getId().equals(id)) {
            throw new AcessoNegadoException();
        }
    }

    // ------------------------------------------------------------------ E/S HTTP

    private <T> T lerCorpo(HttpExchange ex, Class<T> tipo) throws IOException {
        byte[] bytes = ex.getRequestBody().readNBytes(TAMANHO_MAX_CORPO + 1);
        if (bytes.length > TAMANHO_MAX_CORPO) {
            throw new ValidacaoException("Corpo da requisição maior que " + TAMANHO_MAX_CORPO + " bytes.");
        }
        if (bytes.length == 0) {
            throw new ValidacaoException("O corpo da requisição (JSON) é obrigatório.");
        }
        try {
            T valor = json.readValue(bytes, tipo);
            if (valor == null) {
                throw new ValidacaoException("JSON inválido.");
            }
            return valor;
        } catch (JsonProcessingException e) {
            throw new ValidacaoException("JSON inválido.");
        }
    }

    private static long lerId(String texto) {
        try {
            return Long.parseLong(texto);
        } catch (NumberFormatException e) {
            throw new ValidacaoException("Identificador inválido: " + texto);
        }
    }

    private void metodoNaoPermitido(HttpExchange ex, String permitidos) throws IOException {
        ex.getResponseHeaders().set("Allow", permitidos);
        enviar(ex, 405, new ErroResponse(405, "Método não permitido."));
    }

    private void responderErro(HttpExchange ex, NegocioException e) throws IOException {
        int status;
        if (e instanceof ValidacaoException) {
            status = 400;
        } else if (e instanceof AutenticacaoException) {
            status = 401;
            ex.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"usuarios\", charset=\"UTF-8\"");
        } else if (e instanceof AcessoNegadoException) {
            status = 403;
        } else if (e instanceof UsuarioNaoEncontradoException) {
            status = 404;
        } else if (e instanceof UsuarioDuplicadoException) {
            status = 409;
        } else {
            status = 400;
        }
        enviar(ex, status, new ErroResponse(status, e.getMessage()));
    }

    private void enviar(HttpExchange ex, int status, Object corpo) throws IOException {
        if (corpo == null) {
            ex.sendResponseHeaders(status, -1);
            return;
        }
        byte[] bytes = json.writeValueAsBytes(corpo);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
