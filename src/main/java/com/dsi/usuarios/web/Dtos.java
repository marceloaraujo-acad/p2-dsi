package com.dsi.usuarios.web;

import com.dsi.usuarios.model.Usuario;

/** Objetos de transferencia (JSON) da API. O hash da senha nunca sai do servidor. */
public final class Dtos {

    private Dtos() {
    }

    /** POST /usuarios */
    public record CadastroRequest(String nome, String usuario, String senha, String confirmacaoSenha) {
    }

    /** PUT /usuarios/{id}; senha e confirmacaoSenha sao opcionais (omitidas = manter a atual). */
    public record AlteracaoRequest(String nome, String usuario, String senha, String confirmacaoSenha) {
    }

    /** POST /login */
    public record LoginRequest(String usuario, String senha) {
    }

    public record UsuarioResponse(long id, String nome, String usuario, String criadoEm) {
        public static UsuarioResponse de(Usuario u) {
            return new UsuarioResponse(
                    u.getId(), u.getNome(), u.getUsuario(),
                    u.getCriadoEm() != null ? u.getCriadoEm().toString() : null);
        }
    }

    public record ErroResponse(int status, String erro) {
    }
}
