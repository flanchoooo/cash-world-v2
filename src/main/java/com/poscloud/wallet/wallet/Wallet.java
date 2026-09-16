package com.poscloud.wallet.wallet;

import com.poscloud.wallet.common.BaseEntity;
import com.poscloud.wallet.common.Types.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "wallets")
public class Wallet extends BaseEntity {
  private String walletNumber;
  private UUID customerId;
  private UUID currencyId;

  @Enumerated(EnumType.STRING)
  private WalletType walletType;

  private String name;

  @Setter(AccessLevel.NONE)
  @Column(precision = 19, scale = 4)
  private BigDecimal balance = BigDecimal.ZERO;

  private boolean allowNegativeBalance;

  @Enumerated(EnumType.STRING)
  private WalletStatus status;

  @Version private long version;

  // Package-private: LedgerService is the only caller allowed to change a balance.
  void applyBalance(BigDecimal value) {
    balance = value;
  }
}
