package com.poscloud.wallet.audit;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {}
