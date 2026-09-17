package com.poscloud.wallet.wallet;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTypeRepository extends JpaRepository<WalletTypeDefinition, UUID> {
  Optional<WalletTypeDefinition> findByCode(String code);
}
