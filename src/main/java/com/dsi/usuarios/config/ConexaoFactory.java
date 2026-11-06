package com.dsi.usuarios.config;

import com.dsi.usuarios.exception.PersistenciaException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Equivalente a classe {@code Conexao} das aulas: centraliza a abertura de conexoes JDBC.
 * Quem pede a conexao e responsavel por fecha-la (use try-with-resources).
 */
public class ConexaoFactory {

    private final String url;
    private final String usuario;
    private final String senha;

    public ConexaoFactory(String url, String usuario, String senha) {
        this.url = url;
        this.usuario = usuario;
        this.senha = senha;
    }

    public static ConexaoFactory deConfig(AppConfig config) {
        return new ConexaoFactory(
                config.get("db.url", "jdbc:h2:file:./data/usuarios;AUTO_SERVER=TRUE"),
                config.get("db.user", "sa"),
                config.get("db.password", ""));
    }

    public Connection abrir() {
        try {
            return DriverManager.getConnection(url, usuario, senha);
        } catch (SQLException e) {
            // A URL nao e logada com credenciais; so a causa original fica encadeada.
            throw new PersistenciaException("Falha ao abrir conexão com o banco de dados.", e);
        }
    }
}
