package com.dsi.usuarios.dao;

import com.dsi.usuarios.exception.PersistenciaException;
import com.dsi.usuarios.exception.UsuarioDuplicadoException;
import com.dsi.usuarios.model.Usuario;

import java.util.List;
import java.util.Optional;

/**
 * Contrato de acesso a dados da entidade {@link Usuario} (padrao DAO).
 * A camada de servico depende desta interface, nao do JDBC.
 *
 * <p>Todas as operacoes podem lancar {@link PersistenciaException} em falhas de infraestrutura.
 */
public interface UsuarioDao {

    /**
     * CREATE. Persiste o usuario e devolve a versao salva (com id e data de criacao).
     *
     * @throws UsuarioDuplicadoException se o login ja existir
     */
    Usuario inserir(Usuario usuario);

    /** READ por chave primaria. */
    Optional<Usuario> buscarPorId(long id);

    /** READ por login (usado na autenticacao). */
    Optional<Usuario> buscarPorLogin(String login);

    /** READ de todos, ordenados por nome. */
    List<Usuario> listar();

    /**
     * UPDATE de nome, login e hash da senha.
     *
     * @return {@code false} se nenhum registro tem esse id
     * @throws UsuarioDuplicadoException se o novo login colidir com outro usuario
     */
    boolean atualizar(Usuario usuario);

    /**
     * DELETE.
     *
     * @return {@code false} se nenhum registro tem esse id
     */
    boolean excluir(long id);

    /** Verifica existencia de um login (equivalente ao {@code verificaUsuario(usuario)} das aulas). */
    boolean existeLogin(String login);
}
