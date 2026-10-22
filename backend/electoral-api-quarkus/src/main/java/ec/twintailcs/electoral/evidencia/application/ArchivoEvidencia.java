package ec.twintailcs.electoral.evidencia.application;

/**
 * Archivo multipart ya leido, con los metadatos que necesita el caso de uso.
 *
 * <p>Es el equivalente del {@code MultipartFile} del backend Spring, sin la dependencia del
 * framework: el resource lee la parte multipart y la pasa asi, para que la capa de aplicacion no
 * dependa de JAX-RS.
 *
 * @param nombre nombre del archivo declarado por el cliente
 * @param contentType content type declarado por el cliente (el backend valida el tipo declarado,
 *     no los bytes)
 * @param size tamano en bytes
 * @param contenido bytes del archivo
 */
public record ArchivoEvidencia(String nombre, String contentType, long size, byte[] contenido) {}
