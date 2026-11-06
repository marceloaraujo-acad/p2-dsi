package com.dsi.usuarios.cli;

import com.dsi.usuarios.exception.NegocioException;
import com.dsi.usuarios.model.Usuario;
import com.dsi.usuarios.service.UsuarioService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;

/**
 * Interface de linha de comando que reproduz o fluxo das telas das aulas:
 * Login -&gt; (Cadastrar) -&gt; Inicio -&gt; Alterar dados / Excluir conta.
 * Apenas converte entrada/saida de texto em chamadas ao {@link UsuarioService}.
 */
public final class MenuConsole {

    /** Entrada encerrada (Ctrl+D / Ctrl+Z ou fim do pipe). */
    private static final class FimDeEntrada extends RuntimeException {
        private static final long serialVersionUID = 1L;

        FimDeEntrada() {
            super(null, null, false, false);
        }
    }

    private final UsuarioService service;
    private final BufferedReader entrada;
    private final PrintStream saida;
    private final boolean ocultarSenha;

    /**
     * @param ocultarSenha se {@code true} e houver console interativo, a senha digitada nao eco.
     */
    public MenuConsole(UsuarioService service, BufferedReader entrada, PrintStream saida, boolean ocultarSenha) {
        this.service = service;
        this.entrada = entrada;
        this.saida = saida;
        this.ocultarSenha = ocultarSenha;
    }

    public void executar() {
        try {
            while (true) {
                saida.println();
                saida.println("=== LOGIN ===");
                saida.println("1 - Entrar");
                saida.println("2 - Cadastrar");
                saida.println("0 - Sair");
                switch (perguntar("Opção: ")) {
                    case "1" -> entrar();
                    case "2" -> cadastrar();
                    case "0" -> {
                        saida.println("Até logo!");
                        return;
                    }
                    default -> saida.println("Opção inválida.");
                }
            }
        } catch (FimDeEntrada e) {
            saida.println();
            saida.println("Entrada encerrada. Até logo!");
        }
    }

    private void entrar() {
        String login = perguntar("Usuário: ");
        String senha = perguntarSenha("Senha: ");
        try {
            telaInicio(service.autenticar(login, senha));
        } catch (NegocioException e) {
            saida.println("[ERRO] " + e.getMessage());
        }
    }

    private void cadastrar() {
        String nome = perguntar("Nome: ");
        String login = perguntar("Usuário: ");
        String senha = perguntarSenha("Senha: ");
        String confirmacao = perguntarSenha("Confirmar senha: ");
        try {
            Usuario u = service.cadastrar(nome, login, senha, confirmacao);
            saida.println("[OK] Usuário '" + u.getUsuario() + "' cadastrado. Faça o login.");
        } catch (NegocioException e) {
            saida.println("[ERRO] " + e.getMessage());
        }
    }

    /** Tela "home": boas-vindas + Read (listar), Update (alterar) e Delete (excluir). */
    private void telaInicio(Usuario logado) {
        boolean voltar = false;
        while (!voltar) {
            saida.println();
            saida.println("=== INÍCIO ===");
            saida.println("Bem-vindo(a), " + logado.getNome() + "!");
            saida.println("1 - Alterar meus dados");
            saida.println("2 - Excluir minha conta");
            saida.println("3 - Listar usuários");
            saida.println("0 - Sair (voltar ao login)");
            switch (perguntar("Opção: ")) {
                case "1" -> voltar = alterar(logado);
                case "2" -> voltar = excluir(logado);
                case "3" -> listar();
                case "0" -> voltar = true;
                default -> saida.println("Opção inválida.");
            }
        }
    }

    /** @return {@code true} se alterou (como nas aulas, volta ao login para autenticar de novo). */
    private boolean alterar(Usuario logado) {
        saida.println("(Enter mantém o valor atual)");
        String nome = valorOuAtual(perguntar("Nome [" + logado.getNome() + "]: "), logado.getNome());
        String login = valorOuAtual(perguntar("Usuário [" + logado.getUsuario() + "]: "), logado.getUsuario());
        String senha = perguntarSenha("Nova senha (Enter = manter): ");
        String confirmacao = senha.isEmpty() ? "" : perguntarSenha("Confirmar nova senha: ");
        try {
            service.alterar(logado.getId(), nome, login, senha, confirmacao);
            saida.println("[OK] Dados alterados. Faça o login novamente.");
            return true;
        } catch (NegocioException e) {
            saida.println("[ERRO] " + e.getMessage());
            return false;
        }
    }

    /** @return {@code true} se excluiu (volta ao login). */
    private boolean excluir(Usuario logado) {
        String resposta = perguntar("Confirma a exclusão de '" + logado.getUsuario() + "'? (s/N): ");
        if (!resposta.equalsIgnoreCase("s")) {
            saida.println("Exclusão cancelada.");
            return false;
        }
        try {
            service.excluir(logado.getId());
            saida.println("[OK] Conta excluída.");
            return true;
        } catch (NegocioException e) {
            saida.println("[ERRO] " + e.getMessage());
            return false;
        }
    }

    private void listar() {
        saida.printf("%-5s %-30s %-20s %s%n", "ID", "NOME", "USUÁRIO", "CRIADO EM");
        for (Usuario u : service.listar()) {
            saida.printf("%-5d %-30s %-20s %s%n", u.getId(), u.getNome(), u.getUsuario(), u.getCriadoEm());
        }
    }

    // ------------------------------------------------------------------ entrada

    private String perguntar(String rotulo) {
        return lerLinha(rotulo).strip();
    }

    private String lerLinha(String rotulo) {
        saida.print(rotulo);
        saida.flush();
        try {
            String linha = entrada.readLine();
            if (linha == null) {
                throw new FimDeEntrada();
            }
            return linha;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String perguntarSenha(String rotulo) {
        java.io.Console console = System.console();
        if (ocultarSenha && console != null) {
            char[] digitado = console.readPassword(rotulo);
            if (digitado == null) {
                throw new FimDeEntrada();
            }
            String senha = new String(digitado);
            java.util.Arrays.fill(digitado, ' ');
            return senha;
        }
        return lerLinha(rotulo); // senha nao e "stripada": espacos podem fazer parte dela
    }

    private static String valorOuAtual(String digitado, String atual) {
        return digitado.isEmpty() ? atual : digitado;
    }
}
