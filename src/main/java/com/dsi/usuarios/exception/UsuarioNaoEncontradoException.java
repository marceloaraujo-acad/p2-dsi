package com.dsi.usuarios.exception;

/** Nenhum usuario com o identificador informado. */
public class UsuarioNaoEncontradoException extends NegocioException {

    private static final long serialVersionUID = 1L;

    public UsuarioNaoEncontradoException(long id) {
        super("Usuário " + id + " não encontrado.");
    }
}
