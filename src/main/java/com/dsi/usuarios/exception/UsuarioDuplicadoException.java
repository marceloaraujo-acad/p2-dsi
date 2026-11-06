package com.dsi.usuarios.exception;

/** Ja existe um usuario com o mesmo login. */
public class UsuarioDuplicadoException extends NegocioException {

    private static final long serialVersionUID = 1L;

    public UsuarioDuplicadoException(String login) {
        super("Já existe um usuário com o login '" + login + "'.");
    }
}
