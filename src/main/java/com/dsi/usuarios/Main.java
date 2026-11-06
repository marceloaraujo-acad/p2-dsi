package com.dsi.usuarios;

import com.dsi.usuarios.cli.MenuConsole;
import com.dsi.usuarios.config.AppConfig;
import com.dsi.usuarios.exception.PersistenciaException;
import com.dsi.usuarios.web.ApiServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

/**
 * Ponto de entrada.
 * <pre>
 *   java -jar crud-usuarios.jar cli   (padrao) menu interativo no terminal
 *   java -jar crud-usuarios.jar api   API REST em http://127.0.0.1:8090 (server.port)
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        String modo = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "cli";
        if (!modo.equals("cli") && !modo.equals("api")) {
            System.err.println("Uso: java -jar crud-usuarios.jar [cli|api]");
            System.exit(1);
        }

        try {
            Aplicacao app = Aplicacao.iniciar(new AppConfig());
            if (modo.equals("api")) {
                executarApi(app);
            } else {
                executarCli(app);
            }
        } catch (PersistenciaException e) {
            System.err.println("Erro de banco de dados: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Causa: " + e.getCause().getMessage());
            }
            System.exit(2);
        } catch (IOException e) {
            System.err.println("Falha de E/S: " + e.getMessage());
            System.exit(3);
        }
    }

    private static void executarCli(Aplicacao app) {
        // Usa o charset do console para que acentos digitados cheguem corretos ao programa.
        Charset charset = System.console() != null ? System.console().charset() : Charset.defaultCharset();
        BufferedReader entrada = new BufferedReader(new InputStreamReader(System.in, charset));
        new MenuConsole(app.service(), entrada, System.out, true).executar();
    }

    private static void executarApi(Aplicacao app) throws IOException {
        String host = app.config().get("server.host", "127.0.0.1");
        int porta = app.config().getInt("server.port", 8090);
        ApiServer api = new ApiServer(app.service(), host, porta);

        CountDownLatch encerrar = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            api.close();
            encerrar.countDown();
        }));
        api.iniciar();
        System.out.println("API no ar em http://" + host + ":" + api.porta() + "  (Ctrl+C para parar)");
        try {
            encerrar.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
