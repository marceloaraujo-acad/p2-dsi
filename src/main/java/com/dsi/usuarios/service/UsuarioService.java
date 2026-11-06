package com.dsi.usuarios.service;

import com.dsi.usuarios.dao.UsuarioDao;
import com.dsi.usuarios.exception.AutenticacaoException;
import com.dsi.usuarios.exception.UsuarioDuplicadoException;
import com.dsi.usuarios.exception.UsuarioNaoEncontradoException;
import com.dsi.usuarios.exception.ValidacaoException;
import com.dsi.usuarios.model.Usuario;
import com.dsi.usuarios.security.PasswordHasher;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Regras de negocio do CRUD de usuarios. E a unica camada que fala com o DAO e com o
 * {@link PasswordHasher}; CLI e API sao apenas adaptadores de entrada/saida sobre ela.
 *
 * <p>Regras (derivadas das telas de Login/Cadastro/Alteracao das notas de aula):
 * nome, usuario e senha obrigatorios; senha e confirmacao iguais; login unico;
 * autenticacao por login + senha.
 */
public class UsuarioService {

    static final int NOME_MAX = 100;
    static final int SENHA_MIN = 8;
    static final int SENHA_MAX = 128; // limita o custo de CPU do PBKDF2 com entradas gigantes
    private static final Pattern LOGIN_VALIDO = Pattern.compile("^[a-z0-9._-]{3,50}$");

    private final UsuarioDao dao;
    private final PasswordHasher hasher;
    /** Hash de uma senha qualquer, usado para gastar o mesmo tempo quando o login nao existe. */
    private final String hashFalso;

    public UsuarioService(UsuarioDao dao, PasswordHasher hasher) {
        this.dao = dao;
        this.hasher = hasher;
        this.hashFalso = hasher.hash("senha-que-nunca-sera-usada");
    }

    // ------------------------------------------------------------------ CREATE

    public Usuario cadastrar(String nome, String login, String senha, String confirmacaoSenha) {
        String nomeLimpo = validarNome(nome);
        String loginLimpo = validarLogin(login);
        validarSenhaNova(senha, confirmacaoSenha);

        if (dao.existeLogin(loginLimpo)) {
            throw new UsuarioDuplicadoException(loginLimpo);
        }
        // Se outro cadastro concorrente passar entre a checagem e o INSERT, a chave unica do
        // banco garante a integridade e o DAO traduz a violacao para UsuarioDuplicadoException.
        return dao.inserir(new Usuario(nomeLimpo, loginLimpo, hasher.hash(senha)));
    }

    // -------------------------------------------------------------------- READ

    /**
     * Valida login e senha. Mesma mensagem para "login inexistente" e "senha errada", e o
     * custo do PBKDF2 e pago nos dois casos (dificulta enumerar usuarios por tempo de resposta).
     */
    public Usuario autenticar(String login, String senha) {
        if (isVazio(login) || isVazio(senha)) {
            throw new AutenticacaoException();
        }
        Optional<Usuario> encontrado = dao.buscarPorLogin(login.strip().toLowerCase(Locale.ROOT));
        if (encontrado.isEmpty()) {
            hasher.verificar(senha, hashFalso);
            throw new AutenticacaoException();
        }
        Usuario u = encontrado.get();
        if (!hasher.verificar(senha, u.getSenhaHash())) {
            throw new AutenticacaoException();
        }
        return u;
    }

    public Usuario buscarPorId(long id) {
        return dao.buscarPorId(id).orElseThrow(() -> new UsuarioNaoEncontradoException(id));
    }

    public List<Usuario> listar() {
        return dao.listar();
    }

    // ------------------------------------------------------------------ UPDATE

    /**
     * @param novaSenha        {@code null} ou vazia = manter a senha atual
     * @param confirmacaoSenha obrigatoria (e igual a novaSenha) quando novaSenha for informada
     */
    public Usuario alterar(long id, String nome, String login, String novaSenha, String confirmacaoSenha) {
        Usuario atual = buscarPorId(id);
        String nomeLimpo = validarNome(nome);
        String loginLimpo = validarLogin(login);

        String hash = atual.getSenhaHash();
        if (!isVazio(novaSenha)) {
            validarSenhaNova(novaSenha, confirmacaoSenha);
            hash = hasher.hash(novaSenha);
        }

        Optional<Usuario> comMesmoLogin = dao.buscarPorLogin(loginLimpo);
        if (comMesmoLogin.isPresent() && !comMesmoLogin.get().getId().equals(id)) {
            throw new UsuarioDuplicadoException(loginLimpo);
        }

        Usuario novo = new Usuario(id, nomeLimpo, loginLimpo, hash, atual.getCriadoEm());
        if (!dao.atualizar(novo)) { // removido por outra sessao entre a leitura e o UPDATE
            throw new UsuarioNaoEncontradoException(id);
        }
        return buscarPorId(id);
    }

    // ------------------------------------------------------------------ DELETE

    public void excluir(long id) {
        if (!dao.excluir(id)) {
            throw new UsuarioNaoEncontradoException(id);
        }
    }

    // --------------------------------------------------------------- validacoes

    private static String validarNome(String nome) {
        if (isVazio(nome)) {
            throw new ValidacaoException("O nome precisa ser informado.");
        }
        String limpo = nome.strip();
        if (limpo.length() > NOME_MAX) {
            throw new ValidacaoException("O nome pode ter no máximo " + NOME_MAX + " caracteres.");
        }
        return limpo;
    }

    /** Normaliza para minusculas: o login e unico sem diferenciar maiusculas de minusculas. */
    private static String validarLogin(String login) {
        if (isVazio(login)) {
            throw new ValidacaoException("O usuário precisa ser informado.");
        }
        String limpo = login.strip().toLowerCase(Locale.ROOT);
        if (!LOGIN_VALIDO.matcher(limpo).matches()) {
            throw new ValidacaoException(
                    "O usuário deve ter de 3 a 50 caracteres: letras, números, '.', '_' ou '-'.");
        }
        return limpo;
    }

    private static void validarSenhaNova(String senha, String confirmacao) {
        if (isVazio(senha)) {
            throw new ValidacaoException("A senha precisa ser informada.");
        }
        if (senha.length() < SENHA_MIN || senha.length() > SENHA_MAX) {
            throw new ValidacaoException(
                    "A senha deve ter entre " + SENHA_MIN + " e " + SENHA_MAX + " caracteres.");
        }
        if (!senha.equals(confirmacao)) {
            throw new ValidacaoException("Os campos senha e confirmar senha precisam ser iguais.");
        }
    }

    private static boolean isVazio(String s) {
        return s == null || s.isBlank();
    }
}
