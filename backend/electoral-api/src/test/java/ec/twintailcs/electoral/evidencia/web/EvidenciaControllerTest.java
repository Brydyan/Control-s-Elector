package ec.twintailcs.electoral.evidencia.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import java.util.List;
import ec.twintailcs.electoral.evidencia.application.EvidenciaService;
import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaNotFoundException;
import ec.twintailcs.electoral.evidencia.web.dto.EvidenciaResponse;
import ec.twintailcs.electoral.evidencia.web.dto.mapper.EvidenciaMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = EvidenciaController.class)
@AutoConfigureMockMvc(addFilters = false)
class EvidenciaControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private EvidenciaService service;

  @MockitoBean private JwtService jwtService;

  @MockitoBean private UsuarioRepository usuarioRepository;

  @MockitoBean private EvidenciaMapper evidenciaMapper;

  @BeforeEach
  void setUpMapper() {
    when(evidenciaMapper.toDto(any()))
        .thenAnswer(
            invocation -> {
              Evidencia e = invocation.getArgument(0);
              return new EvidenciaResponse(
                  e.getPublicId(),
                  e.getJrvId(),
                  e.getCargoId(),
                  e.getUsuarioId(),
                  e.getS3Key(),
                  e.getSizeBytes(),
                  e.getContentType(),
                  e.getEstado(),
                  e.getCreadoEn(),
                  e.getActualizadoEn(),
                  List.of());
            });
  }

  @Test
  void get_withValidUuid_returnsEvidencia() throws Exception {
    UUID publicId = UUID.randomUUID();
    Evidencia evidencia = new Evidencia();
    evidencia.setPublicId(publicId);
    evidencia.setJrvId(10L);
    evidencia.setCargoId(20L);
    evidencia.setUsuarioId(30L);
    evidencia.setS3Key("actas/10/20/test.webp");
    evidencia.setSizeBytes(2048L);
    evidencia.setContentType("image/webp");
    evidencia.setEstado(EstadoEvidencia.EN_REVISION);

    when(service.get(publicId)).thenReturn(evidencia);

    mockMvc
        .perform(get("/api/v1/evidencias/{id}", publicId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(publicId.toString()))
        .andExpect(jsonPath("$.jrvId").value(10))
        .andExpect(jsonPath("$.cargoId").value(20));

    verify(service).get(publicId);
  }

  @Test
  void get_withUnknownUuid_returns404() throws Exception {
    UUID publicId = UUID.randomUUID();
    when(service.get(publicId)).thenThrow(new EvidenciaNotFoundException(publicId));

    mockMvc.perform(get("/api/v1/evidencias/{id}", publicId)).andExpect(status().isNotFound());
  }

  @Test
  void get_withInvalidUuidFormat_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/evidencias/{id}", "not-a-valid-uuid"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void downloadUrl_withValidUuid_returnsUrl() throws Exception {
    UUID publicId = UUID.randomUUID();
    String expectedUrl = "https://s3.amazonaws.com/presigned-url";
    when(service.presignDownload(publicId)).thenReturn(expectedUrl);

    mockMvc
        .perform(get("/api/v1/evidencias/{id}/download-url", publicId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(publicId.toString()))
        .andExpect(jsonPath("$.url").value(expectedUrl));

    verify(service).presignDownload(publicId);
  }

  @Test
  void cambiarEstado_withValidUuid_returnsUpdated() throws Exception {
    UUID publicId = UUID.randomUUID();
    Evidencia evidencia = new Evidencia();
    evidencia.setPublicId(publicId);
    evidencia.setEstado(EstadoEvidencia.APROBADA);

    when(service.cambiarEstado(eq(publicId), eq(EstadoEvidencia.APROBADA))).thenReturn(evidencia);

    String requestBody = "{\"estado\":\"APROBADA\"}";

    mockMvc
        .perform(
            patch("/api/v1/evidencias/{id}/estado", publicId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(publicId.toString()))
        .andExpect(jsonPath("$.estado").value("APROBADA"));

    verify(service).cambiarEstado(publicId, EstadoEvidencia.APROBADA);
  }

  @Test
  void delete_withValidUuid_returnsNoContent() throws Exception {
    UUID publicId = UUID.randomUUID();
    doNothing().when(service).delete(eq(publicId), any());

    mockMvc.perform(delete("/api/v1/evidencias/{id}", publicId)).andExpect(status().isNoContent());
  }
}
