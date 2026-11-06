package com.dsi.usuarios.dao;

import com.dsi.usuarios.config.ConexaoFactory;
import com.dsi.usuarios.exception.PersistenciaException;
import com.dsi.usuarios.exception.UsuarioDuplicadoException;
import com.dsi.usuarios.model.Usuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementacao JDBC do {@link UsuarioDao}.
 *
 * <p>Seguranca: todo valor vindo do usuario entra por parametro ({@code ?}) de um
 * {@link PreparedStatement} - nunca por concatenacao - o que neutraliza SQL Injection.
 * Conexoes, statements e result sets sao fechados com try-with-resources.
 */
public class UsuarioDaoJdbc implements UsuarioDao {

    private static final String COLUNAS = "id, nome, usuario, senha_hash, criado_em";

    private static final String SQL_INSERIR =
            "INSERT INTO usuario (nome, usuario, senha_hash) VALUES (?, ?, ?)";
    private static final String SQL_POR_ID =
            "SELECT " + COLUNAS + " FROM usuario WHERE id = ?";
    private static final String SQL_POR_LOGIN =
            "SELECT " + COLUNAS + " FROM usuario WHERE usuario = ?";
    private static final String SQL_LISTAR =
            "SELECT " + COLUNAS + " FROM usuario ORDER BY nome, id";
    private static final String SQL_ATUALIZAR =
            "UPDATE usuario SET nome = ?, usuario = ?, senha_hash = ? WHERE id = ?";
    private static final String SQL_EXCLUIR =
            "DELETE FROM usuario WHERE id = ?";
    private static final String SQL_EXISTE_LOGIN =
            "SELECT COUNT(*) FROM usuario WHERE usuario = ?";

    private final ConexaoFactory fabrica;

    public UsuarioDaoJdbc(ConexaoFactory fabrica) {
        this.fabrica = fabrica;
    }

    @Override
    public Usuario inserir(Usuario u) {
        long idGerado;
        try (Connection con = fabrica.abrir();
             PreparedStatement ps = con.prepareStatement(SQL_INSERIR, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.getNome());
            ps.setString(2, u.getUsuario());
            ps.setString(3, u.getSenhaHash());
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                if (!chaves.next()) {
                    throw new PersistenciaException("O banco não retornou o id do usuário inserido.", null);
                }
                idGerado = chaves.getLong(1);
            }
        } catch (SQLException e) {
            throw traduzir(e, "inserir usuário", u.getUsuario());
        }
        return buscarPorId(idGerado).orElseThrow(
                () -> new PersistenciaException("Usuário inserido não pôde ser relido (id " + idGerado + ").", null));
    }

    @Override
    public Optional<Usuario> buscarPorId(long id) {
        try (Connection con = fabrica.abrir();
             PreparedStatement ps = con.prepareStatement(SQL_POR_ID)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw traduzir(e, "buscar usuário por id", null);
        }
    }

    @Override
    public Optional<Usuario> buscarPorLogin(String login) {
        try (Connection con = fabrica.abrir();
             PreparedStatement ps = con.prepareStatement(SQL_POR_LOGIN)) {
            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw traduzir(e, "buscar usuário por login", null);
        }
    }

    @Override
    public List<Usuario> listar() {
        List<Usuario> lista = new ArrayList<>();
        try (Connection con = fabrica.abrir();
             PreparedStatement ps = con.prepareStatement(SQL_LISTAR);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        } catch (SQLException e) {
            throw traduzir(e, "listar usuários", null);
        }
        return lista;
    }

    @Override
    public boolean atualizar(Usuario u) {
        try (Connection con = fabrica.abrir();
             PreparedStatement ps = con.prepareStatement(SQL_ATUALIZAR)) {
            ps.setString(1, u.getNome());
            ps.setString(2, u.getUsuario());
            ps.setString(3, u.getSenhaHash());
            ps.setLong(4, u.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw traduzir(e, "atualizar usuário", u.getUsuario());
        }
    }

    @Override
    public boolean excluir(long id) {
        try (Connection con = fabrica.abrir();
             PreparedStatement ps = con.prepareStatement(SQL_EXCLUIR)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw traduzir(e, "excluir usuário", null);
        }
    }

    @Override
    public boolean existeLogin(String login) {
        try (Connection con = fabrica.abrir();
             PreparedStatement ps = con.prepareStatement(SQL_EXISTE_LOGIN)) {
            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw traduzir(e, "verificar login", null);
        }
    }

    private static Usuario mapear(ResultSet rs) throws SQLException {
        Timestamp criado = rs.getTimestamp("criado_em");
        return new Usuario(
                rs.getLong("id"),
                rs.getString("nome"),
                rs.getString("usuario"),
                rs.getString("senha_hash"),
                criado != null ? criado.toLocalDateTime() : null);
    }

    /**
     * SQLState da classe "23" = violacao de integridade (padrao SQL, vale para H2 e MySQL).
     * Como nome/login ja sao validados no servico, na pratica significa colisao da chave unica.
     */
    private static RuntimeException traduzir(SQLException e, String operacao, String login) {
        String estado = e.getSQLState();
        if (estado != null && estado.startsWith("23") && login != null) {
            return new UsuarioDuplicadoException(login);
        }
        return new PersistenciaException("Falha de banco de dados ao " + operacao + ".", e);
    }
}
