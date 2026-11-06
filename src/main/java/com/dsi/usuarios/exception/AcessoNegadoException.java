package com.dsi.usuarios.exception;

/** Usuario autenticado tentando operar sobre um recurso que nao e dele. */
public class AcessoNegadoException extends NegocioException {

    private static final long serialVersionUID = 1L;

    public AcessoNegadoException() {
        super("Você só pode alterar ou excluir a sua própria conta.");
    }
}
