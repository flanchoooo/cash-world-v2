package com.poscloud.wallet.admin;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/remittance-reports")
@RequiredArgsConstructor
public class RemittanceReportController {
  private final RemittanceReportService reports;

  @GetMapping
  public RemittanceReportService.Report report(
      @RequestParam RemittanceReportService.ReportType type,
      @RequestParam(defaultValue = "DAY") RemittanceReportService.PeriodType period,
      @RequestParam(required = false) LocalDate anchor) {
    return reports.report(type, period, anchor);
  }

  @GetMapping("/export")
  public ResponseEntity<byte[]> export(
      @RequestParam RemittanceReportService.ReportType type,
      @RequestParam(defaultValue = "DAY") RemittanceReportService.PeriodType period,
      @RequestParam(required = false) LocalDate anchor,
      @RequestParam RemittanceReportService.ExportFormat format) {
    var export = reports.export(type, period, anchor, format);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(export.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(export.fileName()).build().toString())
        .cacheControl(CacheControl.noStore())
        .body(export.data());
  }
}
