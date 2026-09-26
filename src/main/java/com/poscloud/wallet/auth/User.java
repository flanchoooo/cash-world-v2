package com.poscloud.wallet.auth;

import com.poscloud.wallet.common.BaseEntity;
import com.poscloud.wallet.common.Types.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseEntity {
  private UUID customerId;
  private String username;
  private String mobileNumber;
  private String email;
  private String passwordHash;
  private String mobilePinHash;

  @Enumerated(EnumType.STRING)
  private Role role;

  @Enumerated(EnumType.STRING)
  private UserStatus status;

  private Instant lastLoginAt;
  private long tokenVersion;
  private boolean permissionsCustomized;
}
