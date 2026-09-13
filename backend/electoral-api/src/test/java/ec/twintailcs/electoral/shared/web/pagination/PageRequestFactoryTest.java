package ec.twintailcs.electoral.shared.web.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageRequestFactoryTest {

  private static final Set<String> ALLOWED_SORTS = Set.of("fechaRegistro", "id", "estado");

  private static final String DEFAULT_SORT = "fechaRegistro";

  @Test
  void from_validParams_buildsPageable() {
    Pageable pageable = PageRequestFactory.from(1, 10, "id", "asc", ALLOWED_SORTS, DEFAULT_SORT);

    assertThat(pageable.getPageNumber()).isEqualTo(1);
    assertThat(pageable.getPageSize()).isEqualTo(10);
    assertThat(pageable.getSort().getOrderFor("id")).isNotNull();
    assertThat(pageable.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
  }

  @Test
  void from_blankSortAndDir_fallsBackToDefaults() {
    Pageable pageable = PageRequestFactory.from(0, 20, null, null, ALLOWED_SORTS, DEFAULT_SORT);

    assertThat(pageable.getSort().getOrderFor(DEFAULT_SORT)).isNotNull();
    assertThat(pageable.getSort().getOrderFor(DEFAULT_SORT).getDirection())
        .isEqualTo(Sort.Direction.DESC);
  }

  @Test
  void from_dirIsCaseInsensitive() {
    Pageable pageable =
        PageRequestFactory.from(0, 20, "estado", "ASC", ALLOWED_SORTS, DEFAULT_SORT);

    assertThat(pageable.getSort().getOrderFor("estado").getDirection())
        .isEqualTo(Sort.Direction.ASC);
  }

  @Test
  void from_negativePage_throws400mapped() {
    assertThatThrownBy(
            () -> PageRequestFactory.from(-1, 20, "id", "asc", ALLOWED_SORTS, DEFAULT_SORT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("page must be >= 0");
  }

  @Test
  void from_outOfRangeSize_throws400mapped() {
    assertThatThrownBy(
            () -> PageRequestFactory.from(0, 0, "id", "asc", ALLOWED_SORTS, DEFAULT_SORT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("size must be between 1 and 100");

    assertThatThrownBy(
            () -> PageRequestFactory.from(0, 101, "id", "asc", ALLOWED_SORTS, DEFAULT_SORT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("size must be between 1 and 100");
  }

  @Test
  void from_unknownSort_throws400mapped() {
    assertThatThrownBy(
            () ->
                PageRequestFactory.from(0, 20, "passwordHash", "asc", ALLOWED_SORTS, DEFAULT_SORT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid sort field: passwordHash");
  }

  @Test
  void from_unknownDir_throws400mapped() {
    assertThatThrownBy(
            () -> PageRequestFactory.from(0, 20, "id", "sideways", ALLOWED_SORTS, DEFAULT_SORT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid dir: sideways");
  }
}
