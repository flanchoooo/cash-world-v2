package com.poscloud.wallet.biller;

import com.poscloud.wallet.common.BaseEntity;
import com.poscloud.wallet.common.Types.*;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "billers")
public class Biller extends BaseEntity {
  private String code;
  private String name;

  @Enumerated(EnumType.STRING)
  private BillerCategory category;

  @Enumerated(EnumType.STRING)
  private Status status;

  private boolean supportsValidation;
  private boolean supportsReversal;
  private boolean supportsEnquiry;
}
