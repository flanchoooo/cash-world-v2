package com.poscloud.wallet.admin;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/expenses")
@RequiredArgsConstructor
public class ExpenseController {
  private final ExpenseService service;

  @GetMapping
  public ExpenseService.Page list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(search, status, page, size);
  }

  @PostMapping
  public ExpenseService.View create(@Valid @RequestBody ExpenseService.Request request) {
    return service.create(request);
  }

  @GetMapping("/{id}")
  public ExpenseService.View get(@PathVariable UUID id) {
    return service.get(id);
  }

  @GetMapping("/{id}/attachments")
  public List<ExpenseService.AttachmentView> attachments(@PathVariable UUID id) {
    return service.attachments(id);
  }

  @GetMapping("/{id}/attachments/{attachmentId}")
  public ResponseEntity<byte[]> download(
      @PathVariable UUID id, @PathVariable UUID attachmentId) {
    var file = service.download(id, attachmentId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.fileName()).build().toString())
        .body(file.data());
  }
}
