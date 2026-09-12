package ec.twintailcs.electoral.evidencia.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaInvalidaException;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaRepository;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.storage.s3.S3StorageService;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class SubirEvidenciaUseCaseTest {

  @Mock private EvidenciaRepository repository;
  @Mock private S3StorageService s3;
  @Mock private UsuarioRepository usuarios;
  @Mock private ActaRepository actaRepository;

  private SubirEvidenciaUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new SubirEvidenciaUseCase(repository, s3, usuarios, actaRepository);
  }

  @Test
  void execute_emptyFile_throwsInvalida() {
    MockMultipartFile emptyFile = new MockMultipartFile("file", new byte[0]);
    assertThatThrownBy(() -> useCase.execute(emptyFile, 1L, UUID.randomUUID()))
        .isInstanceOf(EvidenciaInvalidaException.class);
  }

  @Test
  void execute_invalidContentType_throwsInvalida() {
    MockMultipartFile badType =
        new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());
    assertThatThrownBy(() -> useCase.execute(badType, 1L, UUID.randomUUID()))
        .isInstanceOf(EvidenciaInvalidaException.class);
  }

  @Test
  void execute_actaAnulada_throwsInvalida() {
    MockMultipartFile file =
        new MockMultipartFile("file", "test.webp", "image/webp", "image-content".getBytes());
    UUID callerId = UUID.randomUUID();
    Usuario user = new Usuario();
    user.setId(10L);
    when(usuarios.findByPublicId(callerId)).thenReturn(Optional.of(user));

    Acta acta = new Acta();
    acta.setId(1L);
    acta.setEstado(EstadoActa.ANULADA);
    when(actaRepository.findById(1L)).thenReturn(Optional.of(acta));

    assertThatThrownBy(() -> useCase.execute(file, 1L, callerId))
        .isInstanceOf(EvidenciaInvalidaException.class);
  }

  @Test
  void execute_success_uploadsAndSaves() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "test.webp", "image/webp", "image-content".getBytes());
    UUID callerId = UUID.randomUUID();
    Usuario user = new Usuario();
    user.setId(10L);
    Rol rol = new Rol("OBSERVER");
    user.setRol(rol);
    when(usuarios.findByPublicId(callerId)).thenReturn(Optional.of(user));

    Acta acta = new Acta();
    acta.setId(1L);
    acta.setEstado(EstadoActa.BORRADOR);
    acta.setRegistradaPor(user);
    Jrv jrv = new Jrv();
    jrv.setId(100L);
    acta.setJrv(jrv);
    Cargo cargo = new Cargo();
    cargo.setId(200L);
    acta.setCargo(cargo);
    when(actaRepository.findById(1L)).thenReturn(Optional.of(acta));
    when(repository.save(any(Evidencia.class))).thenAnswer(i -> i.getArgument(0));

    Evidencia res = useCase.execute(file, 1L, callerId);

    assertThat(res.getEstado()).isEqualTo(EstadoEvidencia.EN_REVISION);
    assertThat(res.getActa()).isEqualTo(acta);
    assertThat(res.getJrvId()).isEqualTo(100L);
    assertThat(res.getCargoId()).isEqualTo(200L);
    verify(s3)
        .upload(anyString(), any(InputStream.class), eq((long) file.getSize()), eq("image/webp"));
    verify(repository).save(any(Evidencia.class));
  }

  @Test
  void execute_dbFails_cleansUpS3() {
    MockMultipartFile file =
        new MockMultipartFile("file", "test.webp", "image/webp", "image-content".getBytes());
    UUID callerId = UUID.randomUUID();
    Usuario user = new Usuario();
    user.setId(10L);
    Rol rol = new Rol("OBSERVER");
    user.setRol(rol);
    when(usuarios.findByPublicId(callerId)).thenReturn(Optional.of(user));

    Acta acta = new Acta();
    acta.setId(1L);
    acta.setEstado(EstadoActa.BORRADOR);
    acta.setRegistradaPor(user);
    Jrv jrv = new Jrv();
    jrv.setId(100L);
    acta.setJrv(jrv);
    Cargo cargo = new Cargo();
    cargo.setId(200L);
    acta.setCargo(cargo);
    when(actaRepository.findById(1L)).thenReturn(Optional.of(acta));
    when(repository.save(any(Evidencia.class))).thenThrow(new RuntimeException("db error"));

    assertThatThrownBy(() -> useCase.execute(file, 1L, callerId))
        .isInstanceOf(RuntimeException.class);
    verify(s3).delete(anyString());
  }
}
