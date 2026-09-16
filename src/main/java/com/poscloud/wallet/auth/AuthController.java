package com.poscloud.wallet.auth;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthService service;

  @PostMapping("/register")
  public AuthService.UserView register(@Valid @RequestBody AuthService.Register r) {
    return service.register(r);
  }

  @PostMapping("/login")
  public AuthService.Tokens login(@Valid @RequestBody AuthService.Login r) {
    return service.login(r);
  }

  @PostMapping("/refresh")
  public AuthService.Tokens refresh(@Valid @RequestBody AuthService.Refresh r) {
    return service.refresh(r);
  }

  @GetMapping("/me")
  public AuthService.UserView me() {
    return service.me();
  }

  @PutMapping("/users/{id}")
  public AuthService.UserView update(
      @PathVariable UUID id, @Valid @RequestBody AuthService.UpdateUser request) {
    return service.updateUser(id, request);
  }

  @PostMapping("/users/{id}/block")
  public AuthService.UserView block(@PathVariable UUID id) {
    return service.block(id);
  }
}
