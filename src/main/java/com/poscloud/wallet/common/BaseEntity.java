package com.poscloud.wallet.common;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class BaseEntity {
  @Id protected UUID id = UUID.randomUUID();

  @Column(nullable = false, updatable = false)
  protected Instant createdAt = Instant.now();

  @Column(nullable = false)
  protected Instant updatedAt = Instant.now();

  @PreUpdate
  void touch() {
    updatedAt = Instant.now();
  }
}
