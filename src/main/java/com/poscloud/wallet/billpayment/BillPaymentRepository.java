package com.poscloud.wallet.billpayment;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface BillPaymentRepository extends JpaRepository<BillPayment, UUID> {
  Optional<BillPayment> findByTransactionReference(String ref);
}
