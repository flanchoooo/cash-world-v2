package com.poscloud.wallet.customer;

import com.poscloud.wallet.audit.AuditService;
import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.auth.User;
import com.poscloud.wallet.auth.UserRepository;
import com.poscloud.wallet.currency.CurrencyRepository;
import com.poscloud.wallet.wallet.Wallet;
import com.poscloud.wallet.wallet.WalletRepository;
import com.poscloud.wallet.wallet.WalletTypeRepository;
import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {
  private final CustomerRepository customers;
  private final AccessService access;
  private final AuditService audit;
  private final JdbcTemplate jdbc;
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final WalletRepository wallets;
  private final CurrencyRepository currencies;
  private final WalletTypeRepository walletTypes;

  @io.swagger.v3.oas.annotations.media.Schema(name = "CustomerServiceRequest")
  public record Request(
      @Size(max = 100) String firstName,
      @Size(max = 100) String lastName,
      @Size(max = 200) String companyName,
      @Size(max = 100) String registrationNumber,
      @Size(max = 100) String nationalId,
      @NotBlank @Size(max = 40) String mobileNumber,
      @Email @Size(max = 254) String email,
      @Size(max = 500) String address) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "CustomerServiceView")
  public record View(
      UUID id,
      String customerNumber,
      CustomerType customerType,
      String firstName,
      String lastName,
      String companyName,
      String registrationNumber,
      String nationalId,
      String mobileNumber,
      String email,
      String address,
      boolean isAgent,
      AgentType agentType,
      KycStatus kycStatus,
      CustomerStatus status,
      String loginUsername,
      String temporaryPassword) {
    public static View of(Customer c) {
      return of(c, null, null);
    }

    public static View of(Customer c, String loginUsername, String temporaryPassword) {
      return new View(
          c.getId(),
          c.getCustomerNumber(),
          c.getCustomerType(),
          c.getFirstName(),
          c.getLastName(),
          c.getCompanyName(),
          c.getRegistrationNumber(),
          c.getNationalId(),
          c.getMobileNumber(),
          c.getEmail(),
          c.getAddress(),
          c.isAgent(),
          c.getAgentType(),
          c.getKycStatus(),
          c.getStatus(),
          loginUsername,
          temporaryPassword);
    }
  }

  public record ApiCredentialsRequest(
      @NotBlank @Size(max = 100) String username,
      @Size(min = 8, max = 72) String password,
      @Pattern(regexp = "\\d{4}") String mobilePin,
      @NotNull UserStatus status) {}

  public record ApiCredentialsView(
      boolean configured, String username, boolean mobilePinConfigured, UserStatus status) {}

  public Customer find(String number) {
    return customers
        .findByCustomerNumber(number)
        .orElseThrow(() -> new ApiException("CUSTOMER_NOT_FOUND"));
  }

  @Transactional
  public View create(CustomerType type, Request r) {
    access.requireStaff();
    var c = new Customer();
    c.setCustomerType(type);
    apply(c, r);
    jdbc.update(
        "UPDATE number_sequences SET next_value=LAST_INSERT_ID(next_value+1) WHERE name='customer'");
    Long sequence = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    c.setCustomerNumber("CUS%08d".formatted(sequence));
    c.setKycStatus(KycStatus.PENDING);
    c.setStatus(CustomerStatus.ACTIVE);
    customers.save(c);
    var temporaryPassword = temporaryPassword();
    var user = new User();
    user.setCustomerId(c.getId());
    user.setUsername(c.getCustomerNumber());
    user.setMobileNumber(c.getMobileNumber());
    user.setEmail(c.getEmail());
    user.setPasswordHash(passwords.encode(temporaryPassword));
    user.setRole(Role.CUSTOMER);
    user.setStatus(UserStatus.ACTIVE);
    users.save(user);
    createDefaultWallets(c);
    audit.record("CUSTOMER_REGISTERED", "customers", c.getId());
    audit.record("CUSTOMER_LOGIN_CREATED", "users", user.getId());
    return View.of(c, user.getUsername(), temporaryPassword);
  }

  private void createDefaultWallets(Customer customer) {
    for (var typeCode : List.of("AIRTIME", "WALLET", "BILL_PAYMENT")) {
      var type = walletTypes.findByCode(typeCode).orElseThrow(() -> new ApiException("INVALID_WALLET_TYPE"));
      ApiException.require(type.getStatus() == Status.ACTIVE && type.getScope() == WalletScope.CUSTOMER, "INVALID_WALLET_TYPE");
      for (var currencyCode : List.of("USD", "ZWG")) {
        var currency = currencies.findByCode(currencyCode).orElseThrow(() -> new ApiException("INVALID_CURRENCY"));
        ApiException.require(currency.getStatus() == Status.ACTIVE, "INVALID_CURRENCY");
        var wallet = new Wallet();
        wallet.setWalletNumber("WAL" + UUID.randomUUID().toString().replace("-", ""));
        wallet.setCustomerId(customer.getId());
        wallet.setWalletTypeId(type.getId());
        wallet.setWalletType(WalletType.CUSTOMER);
        wallet.setCurrencyId(currency.getId());
        wallet.setName(type.getName() + " " + currency.getCode() + " Wallet");
        wallet.setStatus(WalletStatus.ACTIVE);
        wallets.save(wallet);
      }
    }
  }

  @Transactional
  public View update(String number, Request r) {
    var c = find(number);
    access.customer(c.getId());
    var nationalId = c.getNationalId();
    apply(c, r);
    if (r.nationalId() == null) c.setNationalId(nationalId);
    audit.record("CUSTOMER_UPDATED", "customers", c.getId());
    return View.of(c);
  }

  private void apply(Customer c, Request r) {
    if (c.getCustomerType() == CustomerType.INDIVIDUAL)
      ApiException.require(nonblank(r.firstName()) && nonblank(r.lastName()), "INVALID_INDIVIDUAL");
    else
      ApiException.require(
          nonblank(r.companyName()) && nonblank(r.registrationNumber()), "INVALID_CORPORATE");
    c.setFirstName(r.firstName());
    c.setLastName(r.lastName());
    c.setCompanyName(r.companyName());
    c.setRegistrationNumber(r.registrationNumber());
    c.setNationalId(r.nationalId());
    c.setMobileNumber(r.mobileNumber());
    c.setEmail(r.email());
    c.setAddress(r.address());
  }

  private boolean nonblank(String s) {
    return s != null && !s.isBlank();
  }

  @Transactional(readOnly = true)
  public View get(String number) {
    var c = find(number);
    access.customer(c.getId());
    return View.of(c);
  }

  @Transactional(readOnly = true)
  public ApiCredentialsView credentials(String number) {
    access.requireStaff();
    var customer = find(number);
    return users
        .findByCustomerId(customer.getId())
        .map(
            u ->
                new ApiCredentialsView(
                    true, u.getUsername(), u.getMobilePinHash() != null, u.getStatus()))
        .orElse(
            new ApiCredentialsView(
                false, customer.getCustomerNumber(), false, UserStatus.ACTIVE));
  }

  @Transactional
  public ApiCredentialsView updateCredentials(String number, ApiCredentialsRequest request) {
    access.requireStaff();
    var customer = find(number);
    var username = request.username().trim();
    ApiException.require(!username.isBlank(), "INVALID_USERNAME");
    var existing = users.lockByCustomerId(customer.getId());
    if (existing.isEmpty()) ApiException.require(request.password() != null, "PASSWORD_REQUIRED");
    if (existing.isEmpty()) ApiException.require(request.mobilePin() != null, "MOBILE_PIN_REQUIRED");
    users
        .findByUsername(username)
        .ifPresent(
            match ->
                ApiException.require(
                    existing.isPresent() && match.getId().equals(existing.get().getId()),
                    "USERNAME_ALREADY_EXISTS"));
    var user = existing.orElseGet(User::new);
    if (existing.isEmpty()) {
      user.setCustomerId(customer.getId());
      user.setRole(customer.isAgent() ? Role.AGENT : Role.CUSTOMER);
      user.setStatus(UserStatus.ACTIVE);
      user.setMobileNumber(customer.getMobileNumber());
      user.setEmail(customer.getEmail());
    }
    if (request.password() != null) {
      ApiException.require(
          request.password().getBytes(StandardCharsets.UTF_8).length <= 72,
          "INVALID_PASSWORD");
      user.setPasswordHash(passwords.encode(request.password()));
    }
    if (request.mobilePin() != null) user.setMobilePinHash(passwords.encode(request.mobilePin()));
    var previousUsername = user.getUsername();
    user.setUsername(username);
    user.setStatus(request.status());
    user.setTokenVersion(user.getTokenVersion() + 1);
    users.save(user);
    audit.record(
        existing.isPresent() ? "CUSTOMER_API_CREDENTIALS_UPDATED" : "CUSTOMER_API_CREDENTIALS_CREATED",
        "users",
        user.getId(),
        previousUsername == null ? null : "{\"configured\":true}",
        "{\"usernameChanged\":"
            + !username.equals(previousUsername)
            + ",\"passwordChanged\":"
            + (request.password() != null)
            + ",\"mobilePinChanged\":"
            + (request.mobilePin() != null)
            + ",\"status\":\""
            + request.status()
            + "\""
            + "}");
    return new ApiCredentialsView(
        true, user.getUsername(), user.getMobilePinHash() != null, user.getStatus());
  }

  @Transactional
  public View status(String number, CustomerStatus status) {
    access.requireStaff();
    var c = find(number);
    c.setStatus(status);
    audit.record("CUSTOMER_" + status, "customers", c.getId());
    return View.of(c);
  }

  @Transactional
  public View agent(String number, AgentType type) {
    access.requireStaff();
    var c = find(number);
    c.setAgent(true);
    c.setAgentType(type);
    users.findByCustomerId(c.getId()).ifPresent(user -> user.setRole(Role.AGENT));
    audit.record("CUSTOMER_MADE_AGENT", "customers", c.getId());
    return View.of(c);
  }

  private String temporaryPassword() {
    var bytes = new byte[15];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
