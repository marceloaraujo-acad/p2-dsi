package com.dsi.usuarios;

import com.dsi.usuarios.config.AppConfig;
import com.dsi.usuarios.config.ConexaoFactory;
import com.dsi.usuarios.config.SchemaInitializer;
import com.dsi.usuarios.dao.UsuarioDao;
import com.dsi.usuarios.dao.UsuarioDaoJdbc;
import com.dsi.usuarios.security.PasswordHasher;
import com.dsi.usuarios.service.UsuarioService;

/**
 * Raiz de composicao: monta as camadas (config -&gt; DAO -&gt; servico) em um unico lugar.
 * Reutilizada por {@link Main} e pelos testes.
 */
public final class Aplicacao {

    private final AppConfig config;
    private final UsuarioService service;

    private Aplicacao(AppConfig config, UsuarioService service) {
        this.config = config;
        this.service = service;
    }

    /** Inicializa o banco (cria schema/dados se necessario) e instancia o servico. */
    public static Aplicacao iniciar(AppConfig config) {
        ConexaoFactory fabrica = ConexaoFactory.deConfig(config);
        String dados = config.getBoolean("db.seed", true) ? config.get("db.seed.script", "data.sql") : null;
        SchemaInitializer.inicializar(fabrica, config.get("db.schema", "schema.sql"), dados);

        UsuarioDao dao = new UsuarioDaoJdbc(fabrica);
        PasswordHasher hasher = new PasswordHasher(config.getInt("security.pbkdf2.iterations", 600_000));
        return new Aplicacao(config, new UsuarioService(dao, hasher));
    }

    public AppConfig config() {
        return config;
    }

    public UsuarioService service() {
        return service;
    }
}
