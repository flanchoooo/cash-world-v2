package com.poscloud.wallet.audit;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.*;

@Service
@RequiredArgsConstructor
public class AuditService {
  private final AuditLogRepository repository;

  public void record(String action, String entity, Object id) {
    record(action, entity, id, null, null);
  }

  public void record(String action, String entity, Object id, String before, String after) {
    var log = new AuditLog();
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null) {
      try {
        log.setUserId(UUID.fromString(auth.getName()));
      } catch (IllegalArgumentException ignored) {
      }
    }
    var attrs = RequestContextHolder.getRequestAttributes();
    if (attrs instanceof ServletRequestAttributes a)
      log.setIpAddress(a.getRequest().getRemoteAddr());
    log.setAction(action);
    log.setEntityType(entity);
    log.setEntityId(id.toString());
    log.setBeforeData(before);
    log.setAfterData(after);
    repository.save(log);
  }
}
