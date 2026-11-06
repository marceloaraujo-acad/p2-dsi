package com.dsi.usuarios.config;

import com.dsi.usuarios.exception.PersistenciaException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Cria o schema (e opcionalmente os dados de demonstracao) quando a tabela {@code usuario}
 * ainda nao existe. Idempotente: em execucoes seguintes nao altera nada.
 */
public final class SchemaInitializer {

    private SchemaInitializer() {
    }

    /**
     * @param scriptSchema recurso do classpath com o DDL (ex.: "schema.sql")
     * @param scriptDados  recurso com o DML inicial, ou {@code null} para nao carregar dados
     * @return {@code true} se o schema foi criado agora
     */
    public static boolean inicializar(ConexaoFactory fabrica, String scriptSchema, String scriptDados) {
        try (Connection con = fabrica.abrir()) {
            if (tabelaExiste(con)) {
                return false;
            }
            executar(con, scriptSchema);
            if (scriptDados != null && !scriptDados.isBlank()) {
                executar(con, scriptDados);
            }
            return true;
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao inicializar o schema do banco de dados.", e);
        }
    }

    /**
     * Consulta o catalogo via JDBC (sem provocar erro de SQL, que o H2 registraria em .trace.db).
     * Testa as duas grafias porque H2 guarda nomes em MAIUSCULAS e o MySQL em minusculas.
     */
    private static boolean tabelaExiste(Connection con) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        for (String nome : new String[] {"USUARIO", "usuario"}) {
            try (ResultSet rs = meta.getTables(con.getCatalog(), null, nome, new String[] {"TABLE"})) {
                if (rs.next()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void executar(Connection con, String recurso) throws SQLException {
        for (String comando : dividir(ler(recurso))) {
            try (Statement st = con.createStatement()) {
                st.execute(comando);
            }
        }
    }

    private static String ler(String recurso) {
        try (InputStream in = SchemaInitializer.class.getResourceAsStream("/" + recurso)) {
            if (in == null) {
                throw new PersistenciaException("Script SQL não encontrado no classpath: " + recurso, null);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PersistenciaException("Falha ao ler o script SQL: " + recurso, e);
        }
    }

    /** Remove comentarios de linha ("--") e separa os comandos por ';'. */
    static List<String> dividir(String script) {
        StringBuilder limpo = new StringBuilder();
        for (String linha : script.split("\\R")) {
            String t = linha.strip();
            if (!t.isEmpty() && !t.startsWith("--")) {
                limpo.append(linha).append('\n');
            }
        }
        List<String> comandos = new ArrayList<>();
        for (String parte : limpo.toString().split(";")) {
            if (!parte.isBlank()) {
                comandos.add(parte.strip());
            }
        }
        return comandos;
    }
}
