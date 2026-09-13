package ec.twintailcs.electoral.shared.web.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PagedResponseTest {

  @Test
  void of_list_computesCeilTotalPages() {
    PagedResponse<String> response = PagedResponse.of(List.of("a", "b"), 0, 2, 5);

    assertThat(response.data()).containsExactly("a", "b");
    assertThat(response.meta().page()).isZero();
    assertThat(response.meta().size()).isEqualTo(2);
    assertThat(response.meta().totalElements()).isEqualTo(5);
    assertThat(response.meta().totalPages()).isEqualTo(3);
  }

  @Test
  void of_list_emptyYieldsZeroPages() {
    PagedResponse<String> response = PagedResponse.of(List.of(), 0, 20, 0);

    assertThat(response.data()).isEmpty();
    assertThat(response.meta().totalPages()).isZero();
  }

  @Test
  void of_page_delegatesToListFactory() {
    Page<String> page = new PageImpl<>(List.of("a"), PageRequest.of(1, 10), 25);

    PagedResponse<String> response = PagedResponse.of(page);

    assertThat(response.data()).containsExactly("a");
    assertThat(response.meta().page()).isEqualTo(1);
    assertThat(response.meta().size()).isEqualTo(10);
    assertThat(response.meta().totalElements()).isEqualTo(25);
    assertThat(response.meta().totalPages()).isEqualTo(3);
  }
}
