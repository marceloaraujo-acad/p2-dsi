package com.dsi.usuarios.exception;

/** Dados de entrada ausentes ou fora do formato esperado. */
public class ValidacaoException extends NegocioException {

    private static final long serialVersionUID = 1L;

    public ValidacaoException(String mensagem) {
        super(mensagem);
    }
}
