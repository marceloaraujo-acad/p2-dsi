package com.dsi.usuarios;

import com.dsi.usuarios.exception.AutenticacaoException;
import com.dsi.usuarios.exception.UsuarioDuplicadoException;
import com.dsi.usuarios.exception.UsuarioNaoEncontradoException;
import com.dsi.usuarios.exception.ValidacaoException;
import com.dsi.usuarios.model.Usuario;
import com.dsi.usuarios.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsuarioServiceTest {

    private UsuarioService service;

    @BeforeEach
    void preparar() {
        service = TestSupport.aplicacao().service();
    }

    // ---- CREATE -----------------------------------------------------------

    @Test
    void cadastraEGuardaSomenteOHashDaSenha() {
        Usuario u = service.cadastrar("  Maria Silva ", "Maria.Silva", "Senha@123", "Senha@123");

        assertEquals("Maria Silva", u.getNome());          // aparado
        assertEquals("maria.silva", u.getUsuario());        // login normalizado
        assertNotEquals("Senha@123", u.getSenhaHash());
        assertTrue(u.getSenhaHash().startsWith("pbkdf2_sha256$"));
    }

    @Test
    void camposObrigatorios() {
        assertThrows(ValidacaoException.class, () -> service.cadastrar("", "ana", "Senha@123", "Senha@123"));
        assertThrows(ValidacaoException.class, () -> service.cadastrar(null, "ana", "Senha@123", "Senha@123"));
        assertThrows(ValidacaoException.class, () -> service.cadastrar("Ana", "  ", "Senha@123", "Senha@123"));
        assertThrows(ValidacaoException.class, () -> service.cadastrar("Ana", "ana", "", ""));
        assertThrows(ValidacaoException.class, () -> service.cadastrar("Ana", "ana", null, null));
    }

    @Test
    void senhaEConfirmacaoPrecisamSerIguais() {
        ValidacaoException e = assertThrows(ValidacaoException.class,
                () -> service.cadastrar("Ana", "ana", "Senha@123", "Senha@124"));
        assertTrue(e.getMessage().contains("iguais"));
    }

    @Test
    void regrasDeFormatoDeLoginESenha() {
        assertThrows(ValidacaoException.class, () -> service.cadastrar("Ana", "a", "Senha@123", "Senha@123"));
        assertThrows(ValidacaoException.class, () -> service.cadastrar("Ana", "ana silva", "Senha@123", "Senha@123"));
        assertThrows(ValidacaoException.class, () -> service.cadastrar("Ana", "ana' OR '1'='1", "Senha@123", "Senha@123"));
        assertThrows(ValidacaoException.class, () -> service.cadastrar("Ana", "ana", "curta", "curta"));
        assertThrows(ValidacaoException.class, () -> service.cadastrar("x".repeat(101), "ana", "Senha@123", "Senha@123"));
        String enorme = "a".repeat(129);
        assertThrows(ValidacaoException.class, () -> service.cadastrar("Ana", "ana", enorme, enorme));
    }

    @Test
    void naoPermiteLoginDuplicadoIgnorandoMaiusculas() {
        service.cadastrar("Ana", "ana", "Senha@123", "Senha@123");
        assertThrows(UsuarioDuplicadoException.class,
                () -> service.cadastrar("Outra", "ANA", "Senha@123", "Senha@123"));
    }

    // ---- READ -------------------------------------------------------------

    @Test
    void autenticaComCredenciaisValidas() {
        Usuario criado = service.cadastrar("Ana", "ana", "Senha@123", "Senha@123");
        Usuario logado = service.autenticar(" ANA ", "Senha@123");
        assertEquals(criado.getId(), logado.getId());
    }

    @Test
    void autenticacaoFalhaComMensagemUnicaParaLoginInexistenteOuSenhaErrada() {
        service.cadastrar("Ana", "ana", "Senha@123", "Senha@123");

        AutenticacaoException senhaErrada = assertThrows(AutenticacaoException.class,
                () -> service.autenticar("ana", "errada123"));
        AutenticacaoException inexistente = assertThrows(AutenticacaoException.class,
                () -> service.autenticar("zeca", "Senha@123"));
        assertEquals(senhaErrada.getMessage(), inexistente.getMessage());

        assertThrows(AutenticacaoException.class, () -> service.autenticar("", "Senha@123"));
        assertThrows(AutenticacaoException.class, () -> service.autenticar("ana", ""));
        assertThrows(AutenticacaoException.class, () -> service.autenticar(null, null));
    }

    @Test
    void sqlInjectionNoLoginNaoAutentica() {
        service.cadastrar("Ana", "ana", "Senha@123", "Senha@123");
        assertThrows(AutenticacaoException.class, () -> service.autenticar("' OR '1'='1' --", "qualquer"));
        assertThrows(AutenticacaoException.class, () -> service.autenticar("ana", "' OR '1'='1"));
    }

    @Test
    void buscaEListagem() {
        Usuario ana = service.cadastrar("Ana", "ana", "Senha@123", "Senha@123");
        service.cadastrar("Beto", "beto", "Senha@123", "Senha@123");

        assertEquals("Ana", service.buscarPorId(ana.getId()).getNome());
        assertEquals(2, service.listar().size());
        assertThrows(UsuarioNaoEncontradoException.class, () -> service.buscarPorId(999));
    }

    // ---- UPDATE -----------------------------------------------------------

    @Test
    void alteraNomeELoginMantendoASenhaQuandoNaoInformada() {
        Usuario ana = service.cadastrar("Ana", "ana", "Senha@123", "Senha@123");

        Usuario alterada = service.alterar(ana.getId(), "Ana Maria", "ana.maria", null, null);

        assertEquals("Ana Maria", alterada.getNome());
        assertEquals("ana.maria", alterada.getUsuario());
        assertEquals(ana.getSenhaHash(), alterada.getSenhaHash());
        assertEquals(alterada.getId(), service.autenticar("ana.maria", "Senha@123").getId());
        assertThrows(AutenticacaoException.class, () -> service.autenticar("ana", "Senha@123"));
    }

    @Test
    void alteraASenha() {
        Usuario ana = service.cadastrar("Ana", "ana", "Senha@123", "Senha@123");

        service.alterar(ana.getId(), "Ana", "ana", "NovaSenha@1", "NovaSenha@1");

        assertThrows(AutenticacaoException.class, () -> service.autenticar("ana", "Senha@123"));
        assertEquals(ana.getId(), service.autenticar("ana", "NovaSenha@1").getId());
    }

    @Test
    void alteracaoValidaConfirmacaoDaSenhaEDuplicidade() {
        Usuario ana = service.cadastrar("Ana", "ana", "Senha@123", "Senha@123");
        service.cadastrar("Beto", "beto", "Senha@123", "Senha@123");

        assertThrows(ValidacaoException.class,
                () -> service.alterar(ana.getId(), "Ana", "ana", "NovaSenha@1", "diferente"));
        assertThrows(ValidacaoException.class, () -> service.alterar(ana.getId(), "", "ana", null, null));
        assertThrows(UsuarioDuplicadoException.class, () -> service.alterar(ana.getId(), "Ana", "beto", null, null));
        // manter o proprio login nao e duplicidade
        assertEquals("ana", service.alterar(ana.getId(), "Ana B", "ana", null, null).getUsuario());
        assertThrows(UsuarioNaoEncontradoException.class, () -> service.alterar(999, "X", "xxx", null, null));
    }

    // ---- DELETE -----------------------------------------------------------

    @Test
    void excluiEDepoisNaoAutenticaMais() {
        Usuario ana = service.cadastrar("Ana", "ana", "Senha@123", "Senha@123");

        service.excluir(ana.getId());

        assertThrows(AutenticacaoException.class, () -> service.autenticar("ana", "Senha@123"));
        assertThrows(UsuarioNaoEncontradoException.class, () -> service.buscarPorId(ana.getId()));
        assertThrows(UsuarioNaoEncontradoException.class, () -> service.excluir(ana.getId()));
        assertFalse(service.listar().stream().anyMatch(u -> u.getId().equals(ana.getId())));
    }
}
