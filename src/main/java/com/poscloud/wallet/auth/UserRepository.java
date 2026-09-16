package com.poscloud.wallet.auth;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.role=:role order by u.id")
  List<User> lockAdministrators(@Param("role") com.poscloud.wallet.common.Types.Role role);

  Optional<User> findByUsername(String username);

  Optional<User> findByCustomerId(UUID customerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.customerId=:customerId")
  Optional<User> lockByCustomerId(@Param("customerId") UUID customerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id=:id")
  Optional<User> lockById(@Param("id") UUID id);
}
