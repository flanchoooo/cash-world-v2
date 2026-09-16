package com.poscloud.wallet.customer;

import com.poscloud.wallet.common.BaseEntity;
import com.poscloud.wallet.common.Types.*;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {
  private String customerNumber;

  @Enumerated(EnumType.STRING)
  private CustomerType customerType;

  private String firstName;
  private String lastName;
  private String companyName;
  private String registrationNumber;
  private String nationalId;
  private String mobileNumber;
  private String email;
  private String address;
  private boolean isAgent;

  @Enumerated(EnumType.STRING)
  private AgentType agentType;

  @Enumerated(EnumType.STRING)
  private KycStatus kycStatus;

  @Enumerated(EnumType.STRING)
  private CustomerStatus status;
}
