package com.poscloud.wallet.common;

public final class Types {
  private Types() {}

  public enum Role {
    SUPER_ADMIN,
    OPERATIONS,
    CUSTOMER,
    CORPORATE_ADMIN,
    CORPORATE_USER,
    AGENT
  }

  public enum UserStatus {
    ACTIVE,
    BLOCKED,
    DISABLED
  }

  public enum CustomerType {
    INDIVIDUAL,
    CORPORATE
  }

  public enum CustomerStatus {
    ACTIVE,
    BLOCKED,
    SUSPENDED,
    CLOSED
  }

  public enum AgentType {
    STANDARD,
    SUPER_AGENT,
    DISTRIBUTOR
  }

  public enum KycStatus {
    PENDING,
    VERIFIED,
    REJECTED
  }

  public enum Status {
    ACTIVE,
    INACTIVE
  }

  public enum WalletScope {
    CUSTOMER,
    BILLER,
    SYSTEM
  }

  /** Legacy wallet classification retained for transaction compatibility. */
  public enum WalletType {
    CUSTOMER,
    BILLER,
    SYSTEM
  }

  public enum WalletStatus {
    ACTIVE,
    BLOCKED,
    CLOSED
  }

  public enum CalculationType {
    FIXED,
    PERCENTAGE,
    FIXED_PLUS_PERCENTAGE
  }

  public enum BillerCategory {
    ELECTRICITY,
    AIRTIME,
    TV,
    INTERNET,
    INSURANCE,
    SCHOOL,
    MUNICIPALITY,
    REMITTANCE,
    OTHER
  }

  public enum RewardMode {
    NONE,
    DISCOUNT,
    CASHBACK
  }

  public enum RewardType {
    FIXED,
    PERCENTAGE
  }

  public enum EntryType {
    PRINCIPAL,
    FEE,
    COMMISSION,
    REVERSAL,
    ADJUSTMENT
  }

  public enum TransactionStatus {
    INITIATED,
    PENDING,
    SUCCESS,
    FAILED,
    REVERSED
  }

  public enum RemittanceStatus {
    INITIATED,
    PENDING,
    AVAILABLE_FOR_PAYOUT,
    PAID,
    FAILED,
    REVERSED
  }

  public enum Direction {
    CREDIT,
    DEBIT
  }
}
