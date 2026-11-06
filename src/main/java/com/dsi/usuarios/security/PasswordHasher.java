package com.dsi.usuarios.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hash de senhas com PBKDF2-HMAC-SHA256 + salt aleatorio por senha.
 *
 * <p>Substitui o MD5 usado nas aulas: MD5 e rapido e sem salt, portanto quebravel por
 * tabelas pre-calculadas e forca bruta. Formato armazenado:
 * {@code pbkdf2_sha256$<iteracoes>$<salt Base64>$<hash Base64>} - as iteracoes ficam no
 * proprio valor, entao e possivel aumenta-las no futuro sem invalidar senhas antigas.
 */
public final class PasswordHasher {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final String PREFIXO = "pbkdf2_sha256";
    private static final int TAMANHO_SALT = 16;
    private static final int TAMANHO_CHAVE_BITS = 256;

    private final int iteracoes;
    private final SecureRandom random = new SecureRandom();

    public PasswordHasher(int iteracoes) {
        if (iteracoes < 1) {
            throw new IllegalArgumentException("iteracoes deve ser >= 1");
        }
        this.iteracoes = iteracoes;
    }

    public String hash(String senha) {
        byte[] salt = new byte[TAMANHO_SALT];
        random.nextBytes(salt);
        byte[] derivado = derivar(senha, salt, iteracoes);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIXO + '$' + iteracoes + '$' + b64.encodeToString(salt) + '$' + b64.encodeToString(derivado);
    }

    /** Comparacao em tempo constante; valor armazenado malformado simplesmente nao confere. */
    public boolean verificar(String senha, String armazenado) {
        if (senha == null || armazenado == null) {
            return false;
        }
        String[] partes = armazenado.split("\\$");
        if (partes.length != 4 || !PREFIXO.equals(partes[0])) {
            return false;
        }
        try {
            int it = Integer.parseInt(partes[1]);
            byte[] salt = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            if (it < 1) {
                return false;
            }
            return MessageDigest.isEqual(esperado, derivar(senha, salt, it));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derivar(String senha, byte[] salt, int iteracoes) {
        PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), salt, iteracoes, TAMANHO_CHAVE_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 indisponível nesta JVM", e);
        } finally {
            spec.clearPassword();
        }
    }
}
