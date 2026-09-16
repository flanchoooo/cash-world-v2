package com.poscloud.wallet.transaction;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TransactionController {
  private final TransactionQueryService queries;

  @GetMapping("/transactions/{ref}")
  public TransactionResult get(@PathVariable String ref) {
    return queries.get(ref);
  }

  @GetMapping("/customers/{number}/transactions")
  public List<TransactionResult> history(
      @PathVariable String number,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "50") int limit) {
    return queries.customerHistory(number, offset, limit);
  }
}
