package ec.twintailcs.electoral.acta.application;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.auth.domain.RecursoNoEncontradoException;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.storage.s3.S3StorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import io.quarkus.panache.common.Sort;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

/**
 * Genera el PDF del expediente de impugnacion CNE en memoria (sin tocar disco).
 *
 * <p>Port exacto del {@code ExpedientePdfService} del backend Spring. Estructura del documento:
 *
 * <ol>
 *   <li>Metadatos (fecha UTC, identificador del coordinador).
 *   <li>Tabla comparativa (canton, parroquia, JRV, cargo, totales).
 *   <li>Tabla de votos por candidato.
 *   <li>Imagen de la foto del acta (45% ancho, borde). Si no hay imagen, recuadro de advertencia
 *       "[SIN RESPALDO FOTOGRÁFICO EN EL SISTEMA]".
 *   <li>Pie forense: hash SHA-256 de los bytes generados + id coordinador + timestamp.
 * </ol>
 *
 * <p>La accion queda registrada en audit_log como {@code EXPEDIENTE_PDF_DESCARGADO}.
 */
@ApplicationScoped
public class ExpedientePdfService {

  @Inject ActaRepository repository;
  @Inject S3StorageService s3StorageService;
  @Inject ActaAuditService audit;

  /**
   * Genera el PDF en memoria. Retorna los bytes generados (incluyendo el SHA-256 en el pie).
   *
   * @param actaId id interno del acta
   * @param usuarioId id interno del usuario que descarga el expediente
   */
  @Transactional
  public byte[] generarExpedientePdf(Long actaId, Long usuarioId) {
    ActaRepository.CabeceraExpediente cabecera =
        repository
            .cabeceraExpediente(actaId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Acta no encontrada: " + actaId));
    List<ActaRepository.FilaVoto> votos = repository.votosPorCandidato(actaId);
    Evidencia primeraEvidencia = Evidencia.find("actaId", Sort.by("id"), actaId).firstResult();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Document document = new Document(PageSize.A4, 36, 36, 36, 36);
    try {
      PdfWriter.getInstance(document, out);
      document.open();

      // 1. Metadatos
      document.addTitle("Expediente de Impugnación - Acta " + cabecera.actaId());
      document.addAuthor("Sistema Electoral Santa Elena");
      document.addCreator("ExpedientePdfService");

      Font h1 = new Font(Font.HELVETICA, 16, Font.BOLD);
      document.add(new Paragraph("EXPEDIENTE DE IMPUGNACIÓN — CNE", h1));
      document.add(new Paragraph(" "));
      document.add(
          new Paragraph("Acta #" + cabecera.actaId() + " — Estado: " + cabecera.estado().name()));
      document.add(
          new Paragraph(
              "Fecha emisión: " + DateTimeFormatter.ISO_INSTANT.format(Instant.now()) + " UTC"));
      document.add(new Paragraph("Descargado por usuario ID: " + usuarioId));
      document.add(new Paragraph(" "));

      // 2. Tabla comparativa
      document.add(new Paragraph("Datos de la mesa", new Font(Font.HELVETICA, 12, Font.BOLD)));
      PdfPTable meta = new PdfPTable(2);
      meta.setWidthPercentage(100);
      addRow(meta, "Cantón", cabecera.canton());
      addRow(meta, "Parroquia", cabecera.parroquia());
      addRow(meta, "JRV", cabecera.numeroJrv());
      addRow(meta, "Recinto", cabecera.recinto());
      addRow(meta, "Cargo", cabecera.cargoNombre());
      addRow(meta, "Total sufragantes", String.valueOf(cabecera.totalSufragantes()));
      addRow(meta, "Votos blancos", String.valueOf(cabecera.votosBlancos()));
      addRow(meta, "Votos nulos", String.valueOf(cabecera.votosNulos()));
      addRow(meta, "Estado", cabecera.estado().name());
      document.add(meta);
      document.add(new Paragraph(" "));

      // 3. Tabla de votos
      document.add(new Paragraph("Votos por candidato", new Font(Font.HELVETICA, 12, Font.BOLD)));
      PdfPTable tablaVotos = new PdfPTable(3);
      tablaVotos.setWidthPercentage(100);
      addHeader(tablaVotos, "Candidato");
      addHeader(tablaVotos, "Partido");
      addHeader(tablaVotos, "Votos");
      for (ActaRepository.FilaVoto voto : votos) {
        addCell(tablaVotos, voto.candidatoNombre());
        addCell(tablaVotos, voto.partidoSiglas());
        addCell(tablaVotos, String.valueOf(voto.votos()));
      }
      document.add(tablaVotos);
      document.add(new Paragraph(" "));

      // 4. Imagen de la foto del acta (o recuadro de advertencia)
      if (primeraEvidencia != null
          && primeraEvidencia.getS3Key() != null
          && !primeraEvidencia.getS3Key().isBlank()) {
        try (InputStream in = s3StorageService.download(primeraEvidencia.getS3Key())) {
          byte[] imageBytes = in.readAllBytes();
          Image img = Image.getInstance(imageBytes);
          float pageWidth = document.getPageSize().getWidth() - 72;
          float scale = (pageWidth * 0.45f) / img.getScaledWidth();
          img.scalePercent(scale * 100);
          img.setBorder(Rectangle.BOX);
          img.setBorderColor(Color.BLACK);
          img.setBorderWidth(1f);
          document.add(img);
        } catch (IOException e) {
          document.add(recuadroAdvertencia("Error al leer la imagen: " + e.getMessage()));
        }
      } else {
        document.add(recuadroAdvertencia("[SIN RESPALDO FOTOGRÁFICO EN EL SISTEMA]"));
      }

      // 5. Pie forense
      document.add(new Paragraph(" "));
      byte[] currentBytes = out.toByteArray();
      String sha = sha256Hex(currentBytes);
      document.add(new Paragraph("Trazabilidad forense", new Font(Font.HELVETICA, 10, Font.BOLD)));
      document.add(new Paragraph("SHA-256 del documento: " + sha, new Font(Font.COURIER, 8)));
      document.add(
          new Paragraph(
              "Coordinador que descargó: usuario_id=" + usuarioId, new Font(Font.COURIER, 8)));
      document.add(
          new Paragraph(
              "Timestamp UTC: " + DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
              new Font(Font.COURIER, 8)));

      document.close();
    } catch (Exception e) {
      throw new IllegalStateException("Error generando PDF: " + e.getMessage(), e);
    }

    // 6. Auditoria
    audit.registrarConIp(
        usuarioId,
        "EXPEDIENTE_PDF_DESCARGADO",
        "acta",
        actaId,
        "Expediente PDF descargado, sha256=" + sha256Hex(out.toByteArray()),
        null);

    return out.toByteArray();
  }

  private Element recuadroAdvertencia(String texto) {
    PdfPTable box = new PdfPTable(1);
    box.setWidthPercentage(60);
    PdfPCell cell = new PdfPCell(new Phrase(texto, new Font(Font.HELVETICA, 12, Font.BOLD)));
    cell.setBorder(Rectangle.BOX);
    cell.setBorderColor(Color.RED);
    cell.setBorderWidth(2f);
    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
    cell.setPadding(20f);
    box.addCell(cell);
    return box;
  }

  private void addRow(PdfPTable t, String k, String v) {
    PdfPCell ck = new PdfPCell(new Phrase(k, new Font(Font.HELVETICA, 10, Font.BOLD)));
    PdfPCell cv = new PdfPCell(new Phrase(v, new Font(Font.HELVETICA, 10)));
    t.addCell(ck);
    t.addCell(cv);
  }

  private void addHeader(PdfPTable t, String label) {
    PdfPCell cell = new PdfPCell(new Phrase(label, new Font(Font.HELVETICA, 10, Font.BOLD)));
    cell.setBackgroundColor(Color.LIGHT_GRAY);
    t.addCell(cell);
  }

  private void addCell(PdfPTable t, String value) {
    t.addCell(new PdfPCell(new Phrase(value, new Font(Font.HELVETICA, 10))));
  }

  private String sha256Hex(byte[] data) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 no disponible", e);
    }
  }
}
