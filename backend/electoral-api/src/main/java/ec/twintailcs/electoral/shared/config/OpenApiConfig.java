package ec.twintailcs.electoral.shared.config;

import ec.twintailcs.electoral.shared.web.dto.ApiErrorResponseDto;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion programatica del spec OpenAPI 3.1 generado por springdoc.
 *
 * <p>Springdoc ya escanea los controllers y arma paths/operaciones automaticamente; esta clase
 * agrega la metadata que no se puede inferir del codigo: - Info: titulo, version, descripcion,
 * contacto, licencia. - Servers: URLs por entorno (local / staging / prod) para que el Swagger UI
 * tenga el dropdown "Select a server" y los clients generados apunten al ambiente correcto. -
 * SecurityScheme: JWT Bearer HTTP para que el boton "Authorize" del Swagger UI registre el token y
 * se envie en cada request de prueba. - Tags: agrupacion logica por dominio (Auth, Evidencias,
 * futuras Actas/JRVs).
 *
 * <p>Los endpoints siguen protegidos por Spring Security — esta clase solo documenta el contrato;
 * no habilita ni relaja ninguna ruta.
 */
@Configuration
public class OpenApiConfig {

  /** Nombre canonico del security scheme. Se referencia en {@code @SecurityRequirement}. */
  public static final String SECURITY_SCHEME_BEARER = "bearer-jwt";

  @Value("${app.jwt.issuer:control-elector}")
  private String jwtIssuer;

  @Value("${server.port:8080}")
  private String serverPort;

  @Bean
  public OpenAPI customOpenAPI() {
    // Descripcion larga de la API: text block (Java 15+) para legibilidad.
    // Markdown ligero — el Swagger UI lo renderiza.
    String apiDescription =
        """
            API REST para el registro, validacion y consolidacion de \
            actas electorales de la Provincia de Santa Elena — \
            Elecciones Seccionales 2027-2031.

            ## Autenticacion
            La mayoria de endpoints requieren JWT Bearer en el header \
            `Authorization: Bearer <access_token>`. Obtener el token \
            via `POST /api/v1/auth/login` y enviarlo en cada request. \
            El Swagger UI incluye el boton **Authorize** (esquina \
            superior derecha) que registra el token globalmente.

            ## Roles
            - `MASTER`: acceso total — registro de catalogos y usuarios, \
              anulacion de actas, revocacion de sesiones.
            - `COORDINATOR`: disputas, correcciones, aprobaciones \
              transversales.
            - `ADMIN`: solo lectura — dashboard y metricas en tiempo real.
            - `OBSERVER`: registro y correccion de actas en sus JRVs asignadas.

            ## Errores
            Las respuestas de error siguen el formato global:
            ```
            { "timestamp": "...", "status": 400, "error": "...", "message": "..." }
            ```
            """;

    // Descripcion del security scheme: concatenacion normal para evitar
    // que un triple-quote interno cierre el text block prematuramente.
    String bearerDescription =
        "JWT firmado HS256 (Nimbus JOSE+JWT). Issuer: **"
            + jwtIssuer
            + "**. Obtener via POST /api/v1/auth/login con email + password. "
            + "TTL access: 15 min (default). Refresh via cookie httpOnly o body.";

    return new OpenAPI()
        .info(
            new Info()
                .title("Control Electoral API")
                .version("0.0.1-SNAPSHOT")
                .description(apiDescription)
                .contact(
                    new Contact()
                        .name("Twin Tail CS")
                        .url("https://twintailcs.xyz")
                        .email("dev@twintailcs.xyz"))
                .license(
                    new License()
                        .name("Propietario - Twin Tail CS")
                        .url("https://twintailcs.xyz/legal")))
        .servers(
            List.of(
                new Server().url("http://localhost:" + serverPort).description("Local (dev)"),
                new Server().url("https://api-staging.twintailcs.xyz").description("Staging"),
                new Server().url("https://api.twintailcs.xyz").description("Produccion")))
        // Aplicar el scheme bearer-jwt globalmente. Cada operacion puede
        // sobreescribirlo con @SecurityRequirement(name = "") si es publico.
        .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_BEARER))
        .tags(
            List.of(
                new Tag()
                    .name("Auth")
                    .description("Login, refresh, logout, me y arbol de menus dinamico."),
                new Tag()
                    .name("Administracion")
                    .description(
                        "Gestion de usuarios y revocacion de sesiones concurrentes (MASTER)."),
                new Tag()
                    .name("Configuracion Electoral")
                    .description("Catalogos de cantones, cargos, partidos politicos y candidatos."),
                new Tag()
                    .name("Evidencias")
                    .description(
                        "Subida, descarga, estados y auditoria de actas fisicas en S3/MinIO."),
                new Tag()
                    .name("Dashboard")
                    .description(
                        "Metricas consolidadas, avance cantonal y novedades en tiempo real."),
                new Tag()
                    .name("Actas - Observador")
                    .description(
                        "Registro, borradores, correcciones, fotos e incidencias por veedor en JRV."),
                new Tag()
                    .name("Actas - Coordinador")
                    .description(
                        "Revision, disputa, correccion, aprobacion, anulacion y expediente PDF.")))
        .components(components(bearerDescription));
  }

  private Components components(String bearerDescription) {
    Components c =
        new Components()
            .addSecuritySchemes(
                SECURITY_SCHEME_BEARER,
                new SecurityScheme()
                    .name(SECURITY_SCHEME_BEARER)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description(bearerDescription));
    // Registro explicito del DTO de error canonico para que aparezca en la
    // seccion Components/Schemas del spec y pueda referenciarse sin necesidad
    // de que springdoc lo encuentre por escaneo de controllers.
    ResolvedSchema resolved =
        ModelConverters.getInstance()
            .resolveAsResolvedSchema(new AnnotatedType(ApiErrorResponseDto.class));
    if (resolved != null && resolved.schema != null) {
      c.addSchemas("ApiErrorResponse", resolved.schema);
    }
    return c;
  }
}
