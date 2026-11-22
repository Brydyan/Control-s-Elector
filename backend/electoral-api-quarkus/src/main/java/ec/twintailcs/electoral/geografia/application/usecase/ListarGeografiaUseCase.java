package ec.twintailcs.electoral.geografia.application.usecase;

import ec.twintailcs.electoral.geografia.web.dto.GeografiaTreeDto;
import ec.twintailcs.electoral.shared.cache.CacheDeLectura;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lectura del arbol geografico completo (provincia → canton → parroquia → JRV).
 *
 * <p>Una sola consulta con LEFT JOINs y el arbol se arma en memoria. Los JOINs son los que
 * sostienen la pertenencia: sin el join a {@code canton}, una provincia se quedaria sin cantones;
 * sin el join a {@code parroquia}, las parroquias quedarian sueltas del canton que las contiene.
 */
@ApplicationScoped
public class ListarGeografiaUseCase {

  @Inject EntityManager entityManager;
  @Inject CacheDeLectura cache;

  /**
   * Devuelve el arbol completo, con listas vacias en los niveles sin hijos. Cacheado con TTL de 10
   * minutos.
   */
  @Transactional
  public GeografiaTreeDto execute() {
    Optional<GeografiaTreeDto> cacheado = cache.leer("geografia", "todos");
    if (cacheado.isPresent()) {
      return cacheado.get();
    }
    GeografiaTreeDto arbol = armarArbol();
    cache.guardar("geografia", "todos", arbol);
    return arbol;
  }

  /**
   * Arma el arbol con una sola consulta.
   *
   * <p>El acceso a la cache va explicito (y no con {@code @CacheResult}) para que el hit ratio
   * quede medido: ver {@link CacheDeLectura}.
   */
  private GeografiaTreeDto armarArbol() {
    List<Object[]> filas =
        entityManager
            .createNativeQuery(
                "select p.id, p.nombre, p.codigo, "
                    + "c.id, c.nombre, c.codigo, "
                    + "pa.id, pa.nombre, pa.es_urbana, "
                    + "j.id, j.numero, j.recinto, j.total_electores "
                    + "from provincia p "
                    + "left join canton c on c.provincia_id = p.id "
                    + "left join parroquia pa on pa.canton_id = c.id "
                    + "left join jrv j on j.parroquia_id = pa.id "
                    + "order by p.nombre asc, c.nombre asc, pa.nombre asc, j.numero asc",
                Object[].class)
            .getResultList();

    Map<Long, NodoProvincia> provincias = new LinkedHashMap<>();
    for (Object[] fila : filas) {
      NodoProvincia provincia =
          provincias.computeIfAbsent(
              id(fila, 0), k -> new NodoProvincia(id(fila, 0), txt(fila, 1), txt(fila, 2)));

      if (fila[3] == null) {
        continue;
      }
      NodoCanton canton =
          provincia.cantones.computeIfAbsent(
              id(fila, 3), k -> new NodoCanton(id(fila, 3), txt(fila, 4), txt(fila, 5)));

      if (fila[6] == null) {
        continue;
      }
      NodoParroquia parroquia =
          canton.parroquias.computeIfAbsent(
              id(fila, 6),
              k -> new NodoParroquia(id(fila, 6), txt(fila, 7), Boolean.TRUE.equals(fila[8])));

      if (fila[9] == null) {
        continue;
      }
      parroquia.jrvs.add(
          new GeografiaTreeDto.JrvNode(
              id(fila, 9), txt(fila, 10), txt(fila, 11), ((Number) fila[12]).intValue()));
    }

    List<GeografiaTreeDto.ProvinciaNode> nodosProvincia = new ArrayList<>(provincias.size());
    for (NodoProvincia provincia : provincias.values()) {
      List<GeografiaTreeDto.CantonNode> nodosCanton = new ArrayList<>(provincia.cantones.size());
      for (NodoCanton canton : provincia.cantones.values()) {
        List<GeografiaTreeDto.ParroquiaNode> nodosParroquia =
            new ArrayList<>(canton.parroquias.size());
        for (NodoParroquia parroquia : canton.parroquias.values()) {
          nodosParroquia.add(
              new GeografiaTreeDto.ParroquiaNode(
                  parroquia.id, parroquia.nombre, parroquia.esUrbana, List.copyOf(parroquia.jrvs)));
        }
        nodosCanton.add(
            new GeografiaTreeDto.CantonNode(
                canton.id, canton.nombre, canton.codigo, List.copyOf(nodosParroquia)));
      }
      nodosProvincia.add(
          new GeografiaTreeDto.ProvinciaNode(
              provincia.id, provincia.nombre, provincia.codigo, List.copyOf(nodosCanton)));
    }
    return new GeografiaTreeDto(List.copyOf(nodosProvincia));
  }

  private static Long id(Object[] fila, int indice) {
    return ((Number) fila[indice]).longValue();
  }

  private static String txt(Object[] fila, int indice) {
    return (String) fila[indice];
  }

  /** Acumulador de provincia mientras se arma el arbol. */
  private static final class NodoProvincia {
    final Long id;
    final String nombre;
    final String codigo;
    final Map<Long, NodoCanton> cantones = new LinkedHashMap<>();

    NodoProvincia(Long id, String nombre, String codigo) {
      this.id = id;
      this.nombre = nombre;
      this.codigo = codigo;
    }
  }

  /** Acumulador de canton mientras se arma el arbol. */
  private static final class NodoCanton {
    final Long id;
    final String nombre;
    final String codigo;
    final Map<Long, NodoParroquia> parroquias = new LinkedHashMap<>();

    NodoCanton(Long id, String nombre, String codigo) {
      this.id = id;
      this.nombre = nombre;
      this.codigo = codigo;
    }
  }

  /** Acumulador de parroquia mientras se arma el arbol. */
  private static final class NodoParroquia {
    final Long id;
    final String nombre;
    final boolean esUrbana;
    final List<GeografiaTreeDto.JrvNode> jrvs = new ArrayList<>();

    NodoParroquia(Long id, String nombre, boolean esUrbana) {
      this.id = id;
      this.nombre = nombre;
      this.esUrbana = esUrbana;
    }
  }
}
