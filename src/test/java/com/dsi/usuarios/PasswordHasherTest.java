package com.dsi.usuarios;

import com.dsi.usuarios.security.PasswordHasher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher(1000);

    @Test
    void hashConfereComASenhaCorreta() {
        String hash = hasher.hash("Segredo#123");
        assertTrue(hasher.verificar("Segredo#123", hash));
    }

    @Test
    void senhaErradaOuVaziaNaoConfere() {
        String hash = hasher.hash("Segredo#123");
        assertFalse(hasher.verificar("segredo#123", hash));
        assertFalse(hasher.verificar("", hash));
        assertFalse(hasher.verificar(null, hash));
    }

    @Test
    void mesmaSenhaGeraHashesDiferentesPorCausaDoSalt() {
        assertNotEquals(hasher.hash("igual"), hasher.hash("igual"));
    }

    @Test
    void formatoArmazenadoNaoContemASenha() {
        String hash = hasher.hash("Segredo#123");
        assertTrue(hash.startsWith("pbkdf2_sha256$1000$"));
        assertFalse(hash.contains("Segredo"));
        assertEquals(4, hash.split("\\$").length);
    }

    @Test
    void valorArmazenadoMalformadoNuncaConfere() {
        assertFalse(hasher.verificar("x", "lixo"));
        assertFalse(hasher.verificar("x", "md5$1$a$b"));
        assertFalse(hasher.verificar("x", "pbkdf2_sha256$abc$@@$@@"));
        assertFalse(hasher.verificar("x", "pbkdf2_sha256$0$AA==$AA=="));
        assertFalse(hasher.verificar("x", null));
    }

    @Test
    void hashGeradoComOutraQuantidadeDeIteracoesAindaVerifica() {
        String antigo = new PasswordHasher(500).hash("abc12345");
        assertTrue(hasher.verificar("abc12345", antigo));
    }
}
