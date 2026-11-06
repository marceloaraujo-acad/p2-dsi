-- =====================================================================
-- data.sql | DML de demonstracao (carregado so quando o schema e criado pela 1a vez)
--
-- Contas de teste (apenas para desenvolvimento - troque/exclua em qualquer ambiente real):
--   admin / Admin@123        aluno / Aluno@123
-- As senhas estao armazenadas como hash PBKDF2-HMAC-SHA256 (600.000 iteracoes, salt proprio),
-- gerado por com.dsi.usuarios.security.PasswordHasher - nunca em texto puro.
-- =====================================================================

INSERT INTO usuario (nome, usuario, senha_hash)
VALUES ('Administrador', 'admin',
        'pbkdf2_sha256$600000$WN/IKEU6VvM/hiSxkZjlKw==$SGS+IQU3AI0FKFcXp9cUDxDz5TybAu3njTFhimiN28Q=');

INSERT INTO usuario (nome, usuario, senha_hash)
VALUES ('Aluno de Teste', 'aluno',
        'pbkdf2_sha256$600000$r8Ot+BpPOmchqrFZE81gAA==$LDy1MwGGssAnRMOve/DOZOxOFopx+iSlWspJOpdHkyM=');
