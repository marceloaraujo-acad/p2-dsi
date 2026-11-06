package com.dsi.usuarios.model;

import java.time.LocalDateTime;

/**
 * Entidade de dominio: usuario do sistema (tabela {@code usuario}).
 * Atributos privados + getters/setters (encapsulamento, como visto em aula).
 */
public class Usuario {

    private Long id;
    private String nome;
    private String usuario;
    private String senhaHash;
    private LocalDateTime criadoEm;

    public Usuario() {
    }

    /** Construtor usado antes de persistir (sem id e sem data de criacao). */
    public Usuario(String nome, String usuario, String senhaHash) {
        this.nome = nome;
        this.usuario = usuario;
        this.senhaHash = senhaHash;
    }

    /** Sobrecarga: construtor completo, usado ao ler do banco. */
    public Usuario(Long id, String nome, String usuario, String senhaHash, LocalDateTime criadoEm) {
        this(nome, usuario, senhaHash);
        this.id = id;
        this.criadoEm = criadoEm;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    /** O hash da senha nunca aparece no toString (evita vazar em logs). */
    @Override
    public String toString() {
        return "Usuario{id=" + id + ", nome='" + nome + "', usuario='" + usuario + "', criadoEm=" + criadoEm + '}';
    }
}
