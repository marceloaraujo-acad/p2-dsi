package com.dsi.usuarios;

import com.dsi.usuarios.config.AppConfig;

import java.util.Map;
import java.util.UUID;

/** Utilitarios de teste: cada chamada cria um banco H2 em memoria isolado. */
public final class TestSupport {

    private TestSupport() {
    }

    public static AppConfig config(boolean comDadosDemo) {
        return new AppConfig(Map.of(
                "db.url", "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1",
                "db.user", "sa",
                "db.password", "",
                "db.seed", String.valueOf(comDadosDemo),
                "security.pbkdf2.iterations", "1000")); // rapido nos testes; producao usa 600000
    }

    public static Aplicacao aplicacao() {
        return Aplicacao.iniciar(config(false));
    }
}
