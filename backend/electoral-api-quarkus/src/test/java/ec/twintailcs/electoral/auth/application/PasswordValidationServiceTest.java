package ec.twintailcs.electoral.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reglas de complejidad de la contrasena que elige el usuario al activar su cuenta.
 *
 * <p>Son las cuatro de la decision D8 del design. El test importa por dos razones: una contrasena
 * debil en una cuenta de observador electoral es un problema real, y el resultado <em>no</em> es un
 * booleano sino la lista de requisitos que faltan — de esa lista depende el checklist en tiempo
 * real del frontend, y si los identificadores cambian, el checklist deja de marcar bien los
 * requisitos sin que se note.
 */
class PasswordValidationServiceTest {

  private final PasswordValidationService validator = new PasswordValidationService();

  @Test
  @DisplayName("una contraseña que cumple los cuatro requisitos es válida")
  void unaContrasenaCompletaEsValida() {
    PasswordValidationResult resultado = validator.validate("Abc123!x");

    assertThat(resultado.isValid()).as("debería ser válida").isTrue();
    assertThat(resultado.getMissing()).as("no debe faltar nada").isEmpty();
  }

  @Test
  @DisplayName("una contraseña de menos de 8 caracteres lista el requisito de longitud")
  void unaContrasenaCortaListaLaLongitud() {
    PasswordValidationResult resultado = validator.validate("Ab1!xyz");

    assertThat(resultado.isValid()).as("no debería ser válida").isFalse();
    assertThat(resultado.getMissing()).containsExactly(PasswordValidationResult.REQ_LENGTH);
  }

  @Test
  @DisplayName("una contraseña sin mayúscula lista el requisito de mayúscula")
  void unaContrasenaSinMayusculaListaLaMayuscula() {
    PasswordValidationResult resultado = validator.validate("abc123!x");

    assertThat(resultado.isValid()).as("no debería ser válida").isFalse();
    assertThat(resultado.getMissing()).containsExactly(PasswordValidationResult.REQ_UPPERCASE);
  }

  @Test
  @DisplayName("una contraseña sin número lista el requisito de número")
  void unaContrasenaSinNumeroListaElNumero() {
    // "Abc!xyzZ": 8 caracteres, con mayúscula y con especial, pero sin un solo número.
    PasswordValidationResult resultado = validator.validate("Abc!xyzZ");

    assertThat(resultado.isValid()).as("no debería ser válida").isFalse();
    assertThat(resultado.getMissing()).containsExactly(PasswordValidationResult.REQ_NUMBER);
  }

  @Test
  @DisplayName("una contraseña sin carácter especial lista el requisito de carácter especial")
  void unaContrasenaSinEspecialListaElEspecial() {
    // "Abc1xyzZ": 8 caracteres, con mayúscula y con número, pero sin un solo especial.
    PasswordValidationResult resultado = validator.validate("Abc1xyzZ");

    assertThat(resultado.isValid()).as("no debería ser válida").isFalse();
    assertThat(resultado.getMissing()).containsExactly(PasswordValidationResult.REQ_SPECIAL);
  }

  @Test
  @DisplayName("una contraseña lista todos los requisitos que le faltan, no solo el primero")
  void unaContrasenaListaTodosLosRequisitosQueLeFaltan() {
    PasswordValidationResult resultado = validator.validate("abc");

    assertThat(resultado.getMissing())
        .containsExactlyInAnyOrder(
            PasswordValidationResult.REQ_LENGTH,
            PasswordValidationResult.REQ_UPPERCASE,
            PasswordValidationResult.REQ_NUMBER,
            PasswordValidationResult.REQ_SPECIAL);
  }

  @Test
  @DisplayName("las contraseñas que el spec declara inválidas son rechazadas")
  void lasContrasenasQueElSpecDeclaraInvalidasSonRechazadas() {
    for (String invalida : new String[] {"password", "weak", "ABC", "123456789"}) {
      assertThat(validator.esValida(invalida)).as("«%s» no debería ser válida", invalida).isFalse();
    }
  }

  @Test
  @DisplayName("las contraseñas que el spec declara válidas son aceptadas")
  void lasContrasenasQueElSpecDeclaraValidasSonAceptadas() {
    for (String valida : new String[] {"Abc123!x", "Observer@2025", "Pass$word1"}) {
      assertThat(validator.esValida(valida)).as("«%s» debería ser válida", valida).isTrue();
    }
  }

  @Test
  @DisplayName("una contraseña nula o vacía no rompe el validador")
  void unaContrasenaNulaNoRompeElValidador() {
    assertThat(validator.validate(null).isValid()).as("null no es válida").isFalse();
    assertThat(validator.validate("").isValid()).as("vacía no es válida").isFalse();
  }
}
