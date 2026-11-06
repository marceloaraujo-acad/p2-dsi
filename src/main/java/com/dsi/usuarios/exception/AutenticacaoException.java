package com.dsi.usuarios.exception;

/**
 * Credenciais ausentes ou invalidas. A mensagem e propositalmente generica para nao
 * revelar se o login existe (evita enumeracao de usuarios).
 */
public class AutenticacaoException extends NegocioException {

    private static final long serialVersionUID = 1L;

    public AutenticacaoException() {
        super("Usuário ou senha inválidos.");
    }

    public AutenticacaoException(String mensagem) {
        super(mensagem);
    }
}
