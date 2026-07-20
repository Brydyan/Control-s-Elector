package ec.twintailcs.electoral.electoral.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class JrvCargoId implements Serializable {

    private Long jrvId;
    private Long cargoId;

    public JrvCargoId() {}

    public JrvCargoId(Long jrvId, Long cargoId) {
        this.jrvId = jrvId;
        this.cargoId = cargoId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JrvCargoId)) return false;
        JrvCargoId that = (JrvCargoId) o;
        return Objects.equals(jrvId, that.jrvId) && Objects.equals(cargoId, that.cargoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jrvId, cargoId);
    }

    public Long getJrvId() { return jrvId; }
    public void setJrvId(Long jrvId) { this.jrvId = jrvId; }

    public Long getCargoId() { return cargoId; }
    public void setCargoId(Long cargoId) { this.cargoId = cargoId; }
}
