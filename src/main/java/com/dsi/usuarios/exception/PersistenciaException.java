package com.dsi.usuarios.exception;

/**
 * Falha de infraestrutura (banco indisponivel, SQL invalido...). Encapsula a
 * {@link java.sql.SQLException} original; a mensagem NAO deve ser exibida ao cliente da API.
 */
public class PersistenciaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PersistenciaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
