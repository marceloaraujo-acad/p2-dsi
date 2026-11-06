package com.dsi.usuarios.exception;

/**
 * Raiz das falhas de regra de negocio (entrada invalida, duplicidade, credenciais...).
 * A mensagem e segura para ser exibida ao usuario final.
 */
public class NegocioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NegocioException(String mensagem) {
        super(mensagem);
    }
}
