package com.poscloud.wallet.customer;

import com.poscloud.wallet.common.Types.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {
  private final CustomerService service;

  @io.swagger.v3.oas.annotations.media.Schema(name = "CustomerControllerAgentRequest")
  public record AgentRequest(@NotNull AgentType agentType) {}

  @PostMapping("/individuals")
  public CustomerService.View individual(@Valid @RequestBody CustomerService.Request r) {
    return service.create(CustomerType.INDIVIDUAL, r);
  }

  @PostMapping("/corporates")
  public CustomerService.View corporate(@Valid @RequestBody CustomerService.Request r) {
    return service.create(CustomerType.CORPORATE, r);
  }

  @GetMapping("/{number}")
  public CustomerService.View get(@PathVariable String number) {
    return service.get(number);
  }

  @PutMapping("/{number}")
  public CustomerService.View update(
      @PathVariable String number, @Valid @RequestBody CustomerService.Request r) {
    return service.update(number, r);
  }

  @GetMapping("/{number}/api-credentials")
  public CustomerService.ApiCredentialsView credentials(@PathVariable String number) {
    return service.credentials(number);
  }

  @PutMapping("/{number}/api-credentials")
  public CustomerService.ApiCredentialsView updateCredentials(
      @PathVariable String number,
      @Valid @RequestBody CustomerService.ApiCredentialsRequest request) {
    return service.updateCredentials(number, request);
  }

  @PostMapping("/{number}/activate")
  public CustomerService.View activate(@PathVariable String number) {
    return service.status(number, CustomerStatus.ACTIVE);
  }

  @PostMapping("/{number}/block")
  public CustomerService.View block(@PathVariable String number) {
    return service.status(number, CustomerStatus.BLOCKED);
  }

  @PostMapping("/{number}/make-agent")
  public CustomerService.View agent(
      @PathVariable String number, @Valid @RequestBody AgentRequest r) {
    return service.agent(number, r.agentType());
  }
}
