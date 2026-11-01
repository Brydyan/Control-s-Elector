--liquibase formatted sql

-- =============================================================================
-- 027 - Repara el hash de las cuentas de referencia.
--
-- El changeset 025 las creo con un hash que NO es un BCrypt valido: mide 61
-- caracteres, cuando BCrypt siempre mide exactamente 60, y el ultimo esta de
-- mas. Con ese hash, `BCrypt.checkpw("admin12345", hash)` devuelve false y
-- ninguno de los cinco usuarios puede iniciar sesion:
--
--     mastersolon@example.local      MASTER
--     coordinatorcortes@example.local COORDINATOR
--     adminpalma@example.local        ADMIN
--     observervera@example.local      OBSERVER
--     observerquimis@example.local    OBSERVER
--
-- El 025 lleva el comentario "placeholder - se regenerara en primera carga
-- real", pero nada lo regeneraba. Un changeset de Liquibase corre una sola vez:
-- corregir el 025 alcanza para bases nuevas, NO para las que ya migraron. Por
-- eso este 027 existe y actualiza por email, que es la unica clave estable.
--
-- Los hashes son BCrypt costo 10 de "admin12345", generados con la misma
-- libreria (jbcrypt) y el mismo costo que usa el 025, para no cambiar el
-- costo respecto de lo que ya estaba sembrado.
--
-- NO se corrige el 025 retroactivamente: si se editara su contenido, Liquibase
-- detectaria el cambio de checksum en bases ya migradas y fallaria. Este
-- este archivo deja el 025 como estaba y arregla los datos.
-- =============================================================================

-- changeset electoral:027-01-repara-hash-cuentas-referencia
-- comment: Reemplaza el hash placeholder de 61 caracteres por un BCrypt valido de 60
UPDATE usuario SET password_hash = '$2a$10$sTvscPRKawa5ryJ511Cj/.3uxXkI8ln4VO.SXGAHxIE/EfTldfuyO' WHERE email = 'mastersolon@example.local';
UPDATE usuario SET password_hash = '$2a$10$Hiojk3dy0AKIN4EWAMfBUuG4FbmTiJT.fSCw6ZFk/InMNxc1RUhaG' WHERE email = 'coordinatorcortes@example.local';
UPDATE usuario SET password_hash = '$2a$10$WyvtEySCO7sr3pm.P.lvLub9LSpteYNycpDj.zxePK9tzLKRizSFq' WHERE email = 'adminpalma@example.local';
UPDATE usuario SET password_hash = '$2a$10$kt/F2DbvtfZUKhC1xXOO1e0iNYIrjmq8ONyDhVRV7Xd0dOPBZ3dja' WHERE email = 'observervera@example.local';
UPDATE usuario SET password_hash = '$2a$10$x7sfBt9ArG2XfpSLsLstnuVHPFgnmcqNj8yBCrkWFpTBcPRmWv6fy' WHERE email = 'observerquimis@example.local';
-- rollback: sin operacion. El hash original era un placeholder de 61 caracteres
-- que no correspondia a ninguna contrasena, asi que no hay valor al que volver.