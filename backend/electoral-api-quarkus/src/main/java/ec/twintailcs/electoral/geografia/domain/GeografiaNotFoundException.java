package ec.twintailcs.electoral.geografia.domain;

/** 404. La entidad geografica no existe: provincia, canton, parroquia o JRV. */
public class GeografiaNotFoundException extends RuntimeException {

  public GeografiaNotFoundException(String mensaje) {
    super(mensaje);
  }
}
