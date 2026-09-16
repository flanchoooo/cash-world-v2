package com.poscloud.wallet.remittance;

import com.poscloud.wallet.transaction.TransactionResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/remittances")
@RequiredArgsConstructor
public class RemittanceController {
  private final RemittanceService service;

  @GetMapping("/saved-details")
  public RemittanceService.SavedDetails savedDetails(
      @RequestParam String mobile, @RequestParam String party) {
    return service.savedDetails(mobile, party);
  }

  @PostMapping("/quote")
  public RemittanceService.Quote quote(@Valid @RequestBody RemittanceService.Send r) {
    return service.quote(r);
  }

  @PostMapping("/send")
  public RemittanceService.View send(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody RemittanceService.Send r) {
    var result = service.send(key, r);
    return service.get(result.remittanceReference());
  }

  @PostMapping("/{ref}/payout")
  public TransactionResult payout(
      @PathVariable String ref,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody RemittanceService.Payout r) {
    return service.payout(ref, key, r);
  }

  @PostMapping("/cash-out")
  public TransactionResult cashOut(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody RemittanceService.CashOut r) {
    return service.cashOut(key, r);
  }

  @PostMapping("/cash-out/preview")
  public RemittanceService.CashOutPreview previewCashOut(
      @Valid @RequestBody RemittanceService.CashOut r) {
    return service.previewCashOut(r);
  }

  @GetMapping("/{ref}")
  public RemittanceService.View get(@PathVariable String ref) {
    return service.get(ref);
  }

  @GetMapping("/{ref}/proof-of-payment")
  public ResponseEntity<byte[]> proof(@PathVariable String ref) {
    var proof = service.proof(ref);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(proof.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename(proof.fileName()).build().toString())
        .body(proof.data());
  }

  @PostMapping("/{ref}/receipt-share")
  public RemittanceService.ReceiptShare createReceiptShare(@PathVariable String ref) {
    return service.createReceiptShare(ref);
  }

  @GetMapping("/public/receipts/{token}.pdf")
  public ResponseEntity<byte[]> receipt(@PathVariable String token) {
    var receipt = service.receipt(token);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename(receipt.fileName()).build().toString())
        .cacheControl(CacheControl.noStore())
        .body(receipt.data());
  }
}
