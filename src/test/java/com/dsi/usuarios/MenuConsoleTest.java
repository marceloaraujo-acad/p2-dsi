package com.dsi.usuarios;

import com.dsi.usuarios.cli.MenuConsole;
import com.dsi.usuarios.exception.AutenticacaoException;
import com.dsi.usuarios.service.UsuarioService;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reproduz o roteiro das aulas: cadastrar -> entrar -> alterar -> entrar -> excluir -> falha no login. */
class MenuConsoleTest {

    private String executar(UsuarioService service, String... linhas) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReader(new StringReader(String.join("\n", linhas) + "\n"));
        new MenuConsole(service, in, out, false).executar();
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Test
    void roteiroCompletoDaAula() {
        UsuarioService service = TestSupport.aplicacao().service();

        String saida = executar(service,
                // 2 = cadastrar (senhas diferentes primeiro -> erro)
                "2", "Marcos Souza", "marksous", "Senha@123", "outra",
                // cadastro correto
                "2", "Marcos Souza", "marksous", "Senha@123", "Senha@123",
                // 1 = entrar com senha errada
                "1", "marksous", "errada123",
                // entrar certo -> tela inicial -> 1 = alterar nome (Enter mantem usuario/senha) -> volta ao login
                "1", "marksous", "Senha@123", "1", "Marcos de Souza", "", "",
                // entrar de novo -> 3 listar -> 2 excluir (confirma) -> volta ao login
                "1", "marksous", "Senha@123", "3", "2", "s",
                // login do excluido falha, depois sair
                "1", "marksous", "Senha@123",
                "0");

        assertTrue(saida.contains("precisam ser iguais"), saida);
        assertTrue(saida.contains("cadastrado"), saida);
        assertTrue(saida.contains("Usuário ou senha inválidos"), saida);
        assertTrue(saida.contains("Bem-vindo(a), Marcos Souza!"), saida);
        assertTrue(saida.contains("Dados alterados"), saida);
        assertTrue(saida.contains("Bem-vindo(a), Marcos de Souza!"), saida);
        assertTrue(saida.contains("Conta excluída"), saida);
        assertThrows(AutenticacaoException.class, () -> service.autenticar("marksous", "Senha@123"));
    }

    @Test
    void fimDaEntradaEncerraSemExcecao() {
        UsuarioService service = TestSupport.aplicacao().service();
        assertDoesNotThrow(() -> executar(service, "1", "alguem"));
    }

    @Test
    void opcaoInvalidaNaoQuebra() {
        UsuarioService service = TestSupport.aplicacao().service();
        String saida = executar(service, "9", "abc", "0");
        assertEquals(2, saida.split("Opção inválida", -1).length - 1);
    }
}
