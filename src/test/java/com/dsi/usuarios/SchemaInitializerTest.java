package com.dsi.usuarios;

import com.dsi.usuarios.config.AppConfig;
import com.dsi.usuarios.config.ConexaoFactory;
import com.dsi.usuarios.config.SchemaInitializer;
import com.dsi.usuarios.service.UsuarioService;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaInitializerTest {

    @Test
    void criaSchemaECarregaDadosDeDemonstracaoUmaUnicaVez() throws Exception {
        AppConfig config = TestSupport.config(true);
        ConexaoFactory fabrica = ConexaoFactory.deConfig(config);

        assertTrue(SchemaInitializer.inicializar(fabrica, "schema.sql", "data.sql"));
        assertFalse(SchemaInitializer.inicializar(fabrica, "schema.sql", "data.sql")); // idempotente

        try (Connection c = fabrica.abrir();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM usuario")) {
            rs.next();
            assertEquals(2, rs.getInt(1));
        }
    }

    /** Garante que os hashes do data.sql sao validos: as contas demo realmente logam. */
    @Test
    void contasDeDemonstracaoAutenticam() {
        UsuarioService service = Aplicacao.iniciar(TestSupport.config(true)).service();
        assertEquals("admin", service.autenticar("admin", "Admin@123").getUsuario());
        assertEquals("aluno", service.autenticar("aluno", "Aluno@123").getUsuario());
    }
}
