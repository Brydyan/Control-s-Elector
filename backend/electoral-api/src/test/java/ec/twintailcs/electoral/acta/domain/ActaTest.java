package ec.twintailcs.electoral.acta.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import org.junit.jupiter.api.Test;

class ActaTest {

  @Test
  void setTotalSufragantes_negativo_throwsDomainException() {
    Acta acta = new Acta();
    assertThatThrownBy(() -> acta.setTotalSufragantes(-1))
        .isInstanceOf(ActaInvalidaException.class)
        .hasMessageContaining("no puede ser negativo");
  }

  @Test
  void setVotosBlancos_negativo_throwsDomainException() {
    Acta acta = new Acta();
    assertThatThrownBy(() -> acta.setVotosBlancos(-5))
        .isInstanceOf(ActaInvalidaException.class)
        .hasMessageContaining("no pueden ser negativos");
  }

  @Test
  void setVotosNulos_negativo_throwsDomainException() {
    Acta acta = new Acta();
    assertThatThrownBy(() -> acta.setVotosNulos(-2))
        .isInstanceOf(ActaInvalidaException.class)
        .hasMessageContaining("no pueden ser negativos");
  }

  @Test
  void setMotivoAnulacion_vacio_throwsDomainException() {
    Acta acta = new Acta();
    assertThatThrownBy(() -> acta.setMotivoAnulacion("   "))
        .isInstanceOf(ActaInvalidaException.class)
        .hasMessageContaining("no puede estar vacío");
  }

  @Test
  void validarDatosObligatorios_faltanCampos_throwsDomainException() {
    Acta acta = new Acta();
    assertThatThrownBy(acta::validarDatosObligatorios).isInstanceOf(ActaInvalidaException.class);
  }

  @Test
  void validarDatosObligatorios_completo_noThrows() {
    Acta acta = new Acta();
    acta.setJrv(new Jrv());
    acta.setCargo(new Cargo());
    acta.setRegistradaPor(new Usuario());
    acta.setTotalSufragantes(100);
    acta.setVotosBlancos(5);
    acta.setVotosNulos(5);

    acta.validarDatosObligatorios();
    assertThat(acta.getTotalSufragantes()).isEqualTo(100);
  }
}
