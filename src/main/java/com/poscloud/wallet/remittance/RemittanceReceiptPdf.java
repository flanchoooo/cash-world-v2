package com.poscloud.wallet.remittance;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class RemittanceReceiptPdf {
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'").withZone(ZoneOffset.UTC);

  private RemittanceReceiptPdf() {}

  static byte[] create(
      Remittance remittance, String sourceCurrency, String destinationCurrency, String createdBy) {
    var content = new StringBuilder();
    content.append("1 1 1 rg 0 0 595 842 re f\n");
    content.append("0.09 0.31 0.25 rg 0 690 595 152 re f\n");
    content.append("1 1 1 rg BT /F2 24 Tf 48 790 Td (POSCLOUD) Tj ET\n");
    content.append("0.78 0.90 0.85 rg BT /F1 10 Tf 48 770 Td (REMITTANCE RECEIPT) Tj ET\n");
    content.append("1 1 1 rg BT /F2 16 Tf 48 727 Td (Transaction confirmed) Tj ET\n");
    content.append("0.96 0.98 0.97 rg 36 568 523 92 re f\n");
    content.append("0.09 0.31 0.25 rg BT /F1 10 Tf 56 630 Td (RECIPIENT RECEIVES) Tj ET\n");
    content
        .append("BT /F2 27 Tf 56 594 Td (")
        .append(escape(destinationCurrency + " " + remittance.getPayoutAmount().toPlainString()))
        .append(") Tj ET\n");

    int y = 530;
    y = row(content, y, "Reference", remittance.getRemittanceReference());
    y = row(content, y, "Date", DATE.format(remittance.getCreatedAt()));
    y = row(content, y, "Status", remittance.getStatus().name().replace('_', ' '));
    y = row(content, y, "Sender", remittance.getSenderName());
    y = row(content, y, "Sender national ID", remittance.getSenderIdNumber());
    y = row(content, y, "Recipient", remittance.getReceiverName());
    y = row(content, y, "Recipient mobile", remittance.getReceiverMobile());
    y = row(content, y, "Recipient national ID", remittance.getReceiverIdNumber());
    y = row(
        content,
        y,
        "Amount sent",
        sourceCurrency + " " + remittance.getSendAmount().toPlainString());
    y = row(
        content,
        y,
        "Fee",
        sourceCurrency + " " + remittance.getFeeAmount().toPlainString());
    y = row(
        content,
        y,
        "Total paid",
        sourceCurrency
            + " "
            + remittance.getSendAmount().add(remittance.getFeeAmount()).toPlainString());
    y = row(
        content,
        y,
        "Fee equivalent",
        destinationCurrency + " " + remittance.getDestinationFeeAmount().toPlainString());
    y = row(content, y, "Exchange rate", remittance.getExchangeRate().toPlainString());
    y = row(content, y, "Reason", remittance.getReasonForSending());
    row(content, y, "Created by", createdBy == null ? "Poscloud administration" : createdBy);

    content.append("0.82 0.87 0.85 RG 36 52 523 0.7 re S\n");
    content.append("0.36 0.45 0.41 rg BT /F1 8 Tf 48 34 Td (This receipt confirms the recorded remittance transaction.) Tj ET\n");
    content.append("BT /F1 8 Tf 48 19 Td (The cash-out code is intentionally not included.) Tj ET\n");
    return pdf(content.toString());
  }

  private static int row(StringBuilder content, int y, String label, String value) {
    content
        .append("0.42 0.50 0.47 rg BT /F1 9 Tf 48 ")
        .append(y)
        .append(" Td (")
        .append(escape(label.toUpperCase()))
        .append(") Tj ET\n");
    content
        .append("0.10 0.18 0.15 rg BT /F2 11 Tf 215 ")
        .append(y)
        .append(" Td (")
        .append(escape(shorten(value)))
        .append(") Tj ET\n");
    content
        .append("0.90 0.93 0.92 RG 48 ")
        .append(y - 10)
        .append(" 499 0.5 re S\n");
    return y - 30;
  }

  private static String shorten(String value) {
    if (value == null || value.isBlank()) return "-";
    var clean = value.replaceAll("[\\r\\n]+", " ").trim();
    return clean.length() > 58 ? clean.substring(0, 55) + "..." : clean;
  }

  private static String escape(String value) {
    var ascii = value.replaceAll("[^\\x20-\\x7E]", "?");
    return ascii.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
  }

  private static byte[] pdf(String stream) {
    var objects = new ArrayList<String>();
    objects.add("<< /Type /Catalog /Pages 2 0 R >>");
    objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
    objects.add(
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>");
    objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
    objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>");
    var streamBytes = stream.getBytes(StandardCharsets.US_ASCII);
    objects.add("<< /Length " + streamBytes.length + " >>\nstream\n" + stream + "endstream");

    var output = new ByteArrayOutputStream();
    write(output, "%PDF-1.4\n%POSCLOUD\n");
    List<Integer> offsets = new ArrayList<>();
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
