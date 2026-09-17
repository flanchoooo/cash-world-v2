package com.poscloud.wallet.wallet;

import com.poscloud.wallet.common.BaseEntity;
import com.poscloud.wallet.common.Types.WalletScope;
import com.poscloud.wallet.common.Types.Status;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "wallet_types")
public class WalletTypeDefinition extends BaseEntity {
  @Column(nullable = false, unique = true, length = 40)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 500)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private WalletScope scope;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;
}
