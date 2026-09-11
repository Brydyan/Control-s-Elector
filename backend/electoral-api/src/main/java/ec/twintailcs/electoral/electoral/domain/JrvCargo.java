package ec.twintailcs.electoral.electoral.domain;

import ec.twintailcs.electoral.geografia.domain.Jrv;
import jakarta.persistence.*;

@Entity
@Table(name = "jrv_cargo")
public class JrvCargo {

  @EmbeddedId private JrvCargoId id;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("jrvId")
  @JoinColumn(name = "jrv_id")
  private Jrv jrv;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("cargoId")
  @JoinColumn(name = "cargo_id")
  private Cargo cargo;

  @Column(nullable = false)
  private boolean activo = true;

  public JrvCargo() {}

  public JrvCargo(Jrv jrv, Cargo cargo) {
    this.jrv = jrv;
    this.cargo = cargo;
    this.id = new JrvCargoId(jrv.getId(), cargo.getId());
  }

  public JrvCargoId getId() {
    return id;
  }

  public void setId(JrvCargoId id) {
    this.id = id;
  }

  public Jrv getJrv() {
    return jrv;
  }

  public void setJrv(Jrv jrv) {
    this.jrv = jrv;
  }

  public Cargo getCargo() {
    return cargo;
  }

  public void setCargo(Cargo cargo) {
    this.cargo = cargo;
  }

  public boolean isActivo() {
    return activo;
  }

  public void setActivo(boolean activo) {
    this.activo = activo;
  }
}
