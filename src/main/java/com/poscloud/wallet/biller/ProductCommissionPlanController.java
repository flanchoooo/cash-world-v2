package com.poscloud.wallet.biller;

import com.poscloud.wallet.common.Types.Status;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/product-commission-plans")
@RequiredArgsConstructor
public class ProductCommissionPlanController {
  private final ProductCommissionPlanService service;

  @GetMapping
  public List<ProductCommissionPlanService.View> list() {
    return service.list();
  }

  @PostMapping
  public ProductCommissionPlanService.View create(
      @Valid @RequestBody ProductCommissionPlanService.Request request) {
    return service.create(request);
  }

  @PutMapping("/{id}")
  public ProductCommissionPlanService.View update(
      @PathVariable UUID id,
      @Valid @RequestBody ProductCommissionPlanService.Request request) {
    return service.update(id, request);
  }

  @PostMapping("/{id}/activate")
  public ProductCommissionPlanService.View activate(@PathVariable UUID id) {
    return service.status(id, Status.ACTIVE);
  }

  @PostMapping("/{id}/deactivate")
  public ProductCommissionPlanService.View deactivate(@PathVariable UUID id) {
    return service.status(id, Status.INACTIVE);
  }
}
