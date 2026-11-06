package com.dsi.usuarios;

import com.dsi.usuarios.config.AppConfig;
import com.dsi.usuarios.config.ConexaoFactory;
import com.dsi.usuarios.config.SchemaInitializer;
import com.dsi.usuarios.dao.UsuarioDao;
import com.dsi.usuarios.dao.UsuarioDaoJdbc;
import com.dsi.usuarios.exception.UsuarioDuplicadoException;
import com.dsi.usuarios.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsuarioDaoJdbcTest {

    private UsuarioDao dao;

    @BeforeEach
    void preparar() {
        AppConfig config = TestSupport.config(false);
        ConexaoFactory fabrica = ConexaoFactory.deConfig(config);
        SchemaInitializer.inicializar(fabrica, "schema.sql", null);
        dao = new UsuarioDaoJdbc(fabrica);
    }

    @Test
    void inserirGeraIdEDataDeCriacao() {
        Usuario salvo = dao.inserir(new Usuario("Ana", "ana", "hash"));
        assertNotNull(salvo.getId());
        assertNotNull(salvo.getCriadoEm());
        assertEquals("ana", salvo.getUsuario());
    }

    @Test
    void buscaPorIdELoginEListagem() {
        Usuario b = dao.inserir(new Usuario("Beto", "beto", "h"));
        Usuario a = dao.inserir(new Usuario("Ana", "ana", "h"));

        assertEquals("Beto", dao.buscarPorId(b.getId()).orElseThrow().getNome());
        assertEquals(a.getId(), dao.buscarPorLogin("ana").orElseThrow().getId());
        assertTrue(dao.buscarPorId(9999).isEmpty());

        List<Usuario> todos = dao.listar();
        assertEquals(List.of("Ana", "Beto"), todos.stream().map(Usuario::getNome).toList()); // ordenado por nome
    }

    @Test
    void loginDuplicadoViolaChaveUnica() {
        dao.inserir(new Usuario("Ana", "ana", "h"));
        assertThrows(UsuarioDuplicadoException.class, () -> dao.inserir(new Usuario("Outra Ana", "ana", "h")));
    }

    @Test
    void atualizarEExcluirInformamSeAfetaramAlgumRegistro() {
        Usuario u = dao.inserir(new Usuario("Ana", "ana", "h1"));

        u.setNome("Ana Maria");
        u.setSenhaHash("h2");
        assertTrue(dao.atualizar(u));
        Usuario relido = dao.buscarPorId(u.getId()).orElseThrow();
        assertEquals("Ana Maria", relido.getNome());
        assertEquals("h2", relido.getSenhaHash());

        assertTrue(dao.excluir(u.getId()));
        assertFalse(dao.excluir(u.getId()));
        u.setId(12345L);
        assertFalse(dao.atualizar(u));
    }

    @Test
    void atualizarParaLoginDeOutroUsuarioViolaChaveUnica() {
        dao.inserir(new Usuario("Ana", "ana", "h"));
        Usuario beto = dao.inserir(new Usuario("Beto", "beto", "h"));
        beto.setUsuario("ana");
        assertThrows(UsuarioDuplicadoException.class, () -> dao.atualizar(beto));
    }

    @Test
    void existeLogin() {
        dao.inserir(new Usuario("Ana", "ana", "h"));
        assertTrue(dao.existeLogin("ana"));
        assertFalse(dao.existeLogin("zeca"));
    }

    /** Payloads classicos de SQL Injection sao tratados como texto literal pelo PreparedStatement. */
    @Test
    void sqlInjectionEhNeutralizado() {
        dao.inserir(new Usuario("Ana", "ana", "h"));

        Optional<Usuario> r1 = dao.buscarPorLogin("' OR '1'='1");
        Optional<Usuario> r2 = dao.buscarPorLogin("ana'; DROP TABLE usuario; --");
        assertTrue(r1.isEmpty());
        assertTrue(r2.isEmpty());
        assertFalse(dao.existeLogin("x' OR 1=1 --"));

        // A tabela continua la e o dado continua intacto.
        assertEquals(1, dao.listar().size());

        // E um payload como NOME e gravado literalmente, sem executar nada.
        Usuario malicioso = dao.inserir(new Usuario("Robert'); DROP TABLE usuario;--", "bobby", "h"));
        assertEquals("Robert'); DROP TABLE usuario;--", dao.buscarPorId(malicioso.getId()).orElseThrow().getNome());
        assertEquals(2, dao.listar().size());
    }
}
