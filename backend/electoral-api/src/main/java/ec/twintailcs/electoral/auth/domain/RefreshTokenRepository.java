package ec.twintailcs.electoral.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE RefreshToken rt SET rt.revocado = true WHERE rt.usuario = :usuario")
    void revocarTodosDeUsuario(@Param("usuario") Usuario usuario);
}
