package com.poscloud.wallet.external;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/credit-sales")
@RequiredArgsConstructor
public class CreditSaleAdminController {
  private final CreditSaleAdminService service;
  @GetMapping public List<CreditSaleAdminService.View> list() { return service.list(); }
  @PostMapping("/{id}/mark-collected")
  public CreditSaleAdminService.View collected(@PathVariable UUID id) { return service.collected(id); }
}
