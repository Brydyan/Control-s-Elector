package ec.twintailcs.electoral.auth.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MenuResponseTest {

    @Test
    void shouldConstructWithUuidTypes() {
        UUID id = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        MenuResponse.MenuNode node = new MenuResponse.MenuNode(
                id,
                parentId,
                "test.code",
                "Test Label",
                "icon",
                "/route",
                1,
                true,
                List.of()
        );

        assertThat(node.id()).isEqualTo(id);
        assertThat(node.parentId()).isEqualTo(parentId);
    }
}
