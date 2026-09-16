package com.poscloud.wallet.biller;

import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/customer-product-allocations")
@RequiredArgsConstructor
public class CustomerProductAllocationController {
  private final CustomerProductAllocationService service;

  @GetMapping
  public List<CustomerProductAllocationService.View> list() { return service.list(); }

  @PostMapping("/{id}/activate")
  public CustomerProductAllocationService.View activate(@PathVariable UUID id) {
    return service.status(id, com.poscloud.wallet.common.Types.Status.ACTIVE);
  }

  @PostMapping("/{id}/deactivate")
  public CustomerProductAllocationService.View deactivate(@PathVariable UUID id) {
    return service.status(id, com.poscloud.wallet.common.Types.Status.INACTIVE);
  }

  @GetMapping("/customer/{customerNumber}")
  public List<CustomerProductAllocationService.View> customerAllocations(
      @PathVariable String customerNumber) {
    return service.listForCustomer(customerNumber);
  }

  @PostMapping("/customer/{customerNumber}")
  public CustomerProductAllocationService.View allocateToCustomer(
      @PathVariable String customerNumber,
      @Valid @RequestBody CustomerProductAllocationService.CustomerRequest request) {
    return service.createForCustomer(customerNumber, request);
  }

  @PutMapping("/customer/{customerNumber}/{id}")
  public CustomerProductAllocationService.View updateCustomerAllocation(
      @PathVariable String customerNumber,
      @PathVariable UUID id,
      @Valid @RequestBody CustomerProductAllocationService.CustomerRequest request) {
    return service.updateForCustomer(customerNumber, id, request);
  }

  @DeleteMapping("/customer/{customerNumber}/{id}")
  public CustomerProductAllocationService.View delinkFromCustomer(
      @PathVariable String customerNumber, @PathVariable UUID id) {
    return service.delinkFromCustomer(customerNumber, id);
  }
}
