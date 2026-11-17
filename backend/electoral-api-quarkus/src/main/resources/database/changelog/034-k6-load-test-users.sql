--liquibase formatted sql

-- changeset electoral:034-01-k6-load-test-users context:load-test
-- Usuarios para K6 load testing: 14 OBSERVERs, 5 COORDINATORs, 1 ADMIN
-- SOLO se ejecuta con: --liquibase.contexts=load-test
-- Contraseña: SecurePass123!
-- Hash: $2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG

-- 14 Observers
INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'obs1@k6.local', 'Observer', '1', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 4, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'obs1@k6.local');

INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'obs2@k6.local', 'Observer', '2', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 4, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'obs2@k6.local');

INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'obs3@k6.local', 'Observer', '3', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 4, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'obs3@k6.local');

INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'obs4@k6.local', 'Observer', '4', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 4, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'obs4@k6.local');

INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'obs5@k6.local', 'Observer', '5', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 4, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'obs5@k6.local');

INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'obs6@k6.local', 'Observer', '6', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 4, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'obs6@k6.local');

INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'obs7@k6.local', 'Observer', '7', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 4, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'obs7@k6.local');

INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'obs8@k6.local', 'Observer', '8', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 4, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'obs8@k6.local');

INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'obs9@k6.local', 'Observer', '9', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 4, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'obs9@k6.local');

INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'obs10@k6.local', 'Observer', '10', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 4, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'obs10@k6.local');

INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'obs11@k6.local', 'Observer', '11', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 4, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'obs11@k6.local');

INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'obs12@k6.local', 'Observer', '12', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 4, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'obs12@k6.local');

INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'obs13@k6.local', 'Observer', '13', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 4, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'obs13@k6.local');

INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'obs14@k6.local', 'Observer', '14', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 4, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'obs14@k6.local');

-- 5 Coordinators
INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'coord1@k6.local', 'Coordinator', '1', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 2, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'coord1@k6.local');

INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'coord2@k6.local', 'Coordinator', '2', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 2, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'coord2@k6.local');

INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'coord3@k6.local', 'Coordinator', '3', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 2, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'coord3@k6.local');

INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'coord4@k6.local', 'Coordinator', '4', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 2, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'coord4@k6.local');

INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'coord5@k6.local', 'Coordinator', '5', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 2, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'coord5@k6.local');

-- 1 Admin
INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT 'admin@k6.local', 'Admin', 'K6', '$2a$10$YYOWuXStWZZW/VIhIcssBecX9cArOZanL5a.5OVlJQ6ESLs0B0UYG', 3, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'admin@k6.local');
