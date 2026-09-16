package com.poscloud.wallet.commission;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agents/{number}/commissions")
@RequiredArgsConstructor
public class CommissionController {
  private final CommissionService service;

  @GetMapping
  public List<CommissionService.View> report(
      @PathVariable String number,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "50") int limit) {
    return service.report(number, offset, limit);
  }

  @GetMapping("/summary")
  public List<CommissionService.Summary> summary(@PathVariable String number) {
    return service.summary(number);
  }
}
