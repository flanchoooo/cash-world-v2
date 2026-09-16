package com.poscloud.wallet.transaction;

import java.util.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface LedgerEntryRepository extends Repository<LedgerEntry, UUID> {
  @Query(
      "select l from LedgerEntry l where l.agentCustomerId=:id and l.billerProductId is not null and l.entryType=com.poscloud.wallet.common.Types$EntryType.PRINCIPAL order by l.sequenceNumber")
  List<LedgerEntry> agentBillPayments(UUID id);

  List<LedgerEntry> findByTransactionReferenceOrderBySequenceNumber(String ref);

  @Query(
      "select l from LedgerEntry l where l.debitWalletId=:id or l.creditWalletId=:id order by l.sequenceNumber")
  List<LedgerEntry> forWallet(UUID id);

  @Query(
      "select l from LedgerEntry l where l.customerId=:id or l.debitWalletId in (select w.id from Wallet w where w.customerId=:id) or l.creditWalletId in (select w.id from Wallet w where w.customerId=:id) order by l.sequenceNumber")
  List<LedgerEntry> forCustomer(UUID id);

  @Query(
      "select l from LedgerEntry l where l.entryType=com.poscloud.wallet.common.Types$EntryType.COMMISSION and l.agentCustomerId=:id order by l.sequenceNumber")
  List<LedgerEntry> commissions(UUID id);
}
