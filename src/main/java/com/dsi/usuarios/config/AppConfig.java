package com.dsi.usuarios.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Properties;

/**
 * Leitura de configuracao. Prioridade: sobrescritas explicitas &gt; propriedade de sistema (-D)
 * &gt; variavel de ambiente (DB_URL para a chave db.url) &gt; application.properties &gt; padrao.
 */
public final class AppConfig {

    private final Properties arquivo = new Properties();
    private final Map<String, String> sobrescritas;

    public AppConfig() {
        this(Map.of());
    }

    /** @param sobrescritas valores que vencem qualquer outra fonte (usado nos testes). */
    public AppConfig(Map<String, String> sobrescritas) {
        this.sobrescritas = Map.copyOf(sobrescritas);
        try (InputStream in = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (in != null) {
                arquivo.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler application.properties", e);
        }
    }

    public String get(String chave, String padrao) {
        String valor = sobrescritas.get(chave);
        if (valor == null) {
            valor = System.getProperty(chave);
        }
        if (valor == null) {
            valor = System.getenv(chave.toUpperCase().replace('.', '_'));
        }
        if (valor == null) {
            valor = arquivo.getProperty(chave);
        }
        return valor != null ? valor : padrao;
    }

    public int getInt(String chave, int padrao) {
        String valor = get(chave, null);
        if (valor == null || valor.isBlank()) {
            return padrao;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor inteiro inválido para '" + chave + "': " + valor, e);
        }
    }

    public boolean getBoolean(String chave, boolean padrao) {
        String valor = get(chave, null);
        return valor == null || valor.isBlank() ? padrao : Boolean.parseBoolean(valor.trim());
    }
}
