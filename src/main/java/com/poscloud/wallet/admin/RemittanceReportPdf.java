package com.poscloud.wallet.admin;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class RemittanceReportPdf {
  private static final ZoneId ZONE = ZoneId.of("Africa/Harare");
  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

  private RemittanceReportPdf() {}

  static byte[] create(RemittanceReportService.Report report) {
    int linesPerRecord = Math.max(1, report.columns().size() + 1);
    int recordsPerPage = Math.max(1, 28 / linesPerRecord);
    var pages = new ArrayList<String>();
    if (report.rows().isEmpty()) pages.add(page(report, List.of(), 1, 1));
    else {
      int totalPages = (report.rows().size() + recordsPerPage - 1) / recordsPerPage;
      for (int offset = 0, page = 1; offset < report.rows().size(); offset += recordsPerPage, page++) {
        pages.add(
            page(
                report,
                report.rows().subList(
                    offset, Math.min(offset + recordsPerPage, report.rows().size())),
                page,
                totalPages));
      }
    }
    return document(pages);
  }

  private static String page(
      RemittanceReportService.Report report,
      List<Map<String, Object>> rows,
      int page,
      int totalPages) {
    var content = new StringBuilder();
    content.append("1 1 1 rg 0 0 842 595 re f\n");
    content.append("0.07 0.24 0.22 rg 0 500 842 95 re f\n");
    headerText(content, true, 20, 38, 555, "POSCLOUD");
    headerText(content, true, 15, 38, 528, report.title());
    var from = DATE_TIME.format(report.from().atZone(ZONE));
    var to = DATE_TIME.format(report.to().atZone(ZONE));
    headerText(content, false, 9, 565, 555, report.period() + "  " + from + " - " + to);
    headerText(content, false, 9, 697, 528, "Page " + page + " of " + totalPages);

    int y = 472;
    if (rows.isEmpty()) {
      text(content, false, 12, 38, y, "No records were found for the selected period.");
    } else {
      for (var row : rows) {
        content.append("0.95 0.98 0.97 rg 30 ").append(y - 4).append(" 782 18 re f\n");
        text(content, true, 9, 40, y, "RECORD");
        y -= 18;
        for (var column : report.columns()) {
          text(content, true, 7, 40, y, column.label().toUpperCase(Locale.ROOT));
          text(content, false, 8, 215, y, shorten(row.get(column.key()), 96));
          y -= 14;
        }
        y -= 8;
      }
    }
    content.append("0.82 0.87 0.85 RG 30 34 782 0.7 re S\n");
    text(content, false, 7, 38, 19, "Generated from recorded remittance transactions. Times use Africa/Harare.");
    return content.toString();
  }

  private static void text(
      StringBuilder content, boolean bold, int size, int x, int y, String value) {
    writeText(content, "0.10 0.18 0.16 rg", bold, size, x, y, value);
  }

  private static void headerText(
      StringBuilder content, boolean bold, int size, int x, int y, String value) {
    writeText(content, "1 1 1 rg", bold, size, x, y, value);
  }

  private static void writeText(
      StringBuilder content,
      String color,
      boolean bold,
      int size,
      int x,
      int y,
      String value) {
    content
        .append(color)
        .append(" BT /")
        .append(bold ? "F2" : "F1")
        .append(' ')
        .append(size)
        .append(" Tf ")
        .append(x)
        .append(' ')
        .append(y)
        .append(" Td (")
        .append(escape(value))
        .append(") Tj ET\n");
  }

  private static String shorten(Object value, int max) {
    if (value == null || String.valueOf(value).isBlank()) return "-";
    var text = String.valueOf(value).replaceAll("[\\r\\n]+", " ").trim();
    return text.length() > max ? text.substring(0, max - 3) + "..." : text;
  }

  private static String escape(String value) {
    var ascii = value.replaceAll("[^\\x20-\\x7E]", "?");
    return ascii.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
  }

  private static byte[] document(List<String> streams) {
    var objects = new ArrayList<String>();
    objects.add("<< /Type /Catalog /Pages 2 0 R >>");
    var kids = new StringBuilder();
    for (int i = 0; i < streams.size(); i++) kids.append(5 + i * 2).append(" 0 R ");
    objects.add("<< /Type /Pages /Kids [" + kids + "] /Count " + streams.size() + " >>");
    objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
    objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>");
    for (int i = 0; i < streams.size(); i++) {
      int contentId = 6 + i * 2;
      objects.add(
          "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents "
              + contentId
              + " 0 R >>");
      var bytes = streams.get(i).getBytes(StandardCharsets.US_ASCII);
      objects.add("<< /Length " + bytes.length + " >>\nstream\n" + streams.get(i) + "endstream");
    }

    var output = new ByteArrayOutputStream();
    write(output, "%PDF-1.4\n%POSCLOUD\n");
    var offsets = new ArrayList<Integer>();
    offsets.add(0);
    for (int i = 0; i < objects.size(); i++) {
      offsets.add(output.size());
      write(output, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
    }
    int xref = output.size();
    write(output, "xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
    for (int i = 1; i < offsets.size(); i++)
      write(output, String.format("%010d 00000 n \n", offsets.get(i)));
    write(
        output,
        "trailer\n<< /Size "
            + (objects.size() + 1)
            + " /Root 1 0 R >>\nstartxref\n"
            + xref
            + "\n%%EOF");
    return output.toByteArray();
  }

  private static void write(ByteArrayOutputStream output, String value) {
    output.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
  }
}
