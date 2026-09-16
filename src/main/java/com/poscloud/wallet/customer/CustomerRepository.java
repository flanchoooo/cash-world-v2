package com.poscloud.wallet.customer;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
  Optional<Customer> findByCustomerNumber(String number);

  @Query(
      value =
          "select * from customers where regexp_replace(mobile_number,'[^0-9]','')=:mobile order by updated_at desc limit 1",
      nativeQuery = true)
  Optional<Customer> findByNormalizedMobile(@Param("mobile") String mobile);
}
