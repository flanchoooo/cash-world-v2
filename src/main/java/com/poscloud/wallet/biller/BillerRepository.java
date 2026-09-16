package com.poscloud.wallet.biller;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface BillerRepository extends JpaRepository<Biller, UUID> {
  Optional<Biller> findByCode(String code);
}
