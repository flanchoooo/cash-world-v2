package com.poscloud.wallet.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poscloud.wallet.auth.*;
import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import java.util.*;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Technical request receipts contain no balances. The user row serializes retries across
 * application instances.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {
  private final jakarta.persistence.EntityManager entityManager;
  private final UserRepository users;
  private final AccessService access;
  private final TransactionRecordRepository requests;
  private final ObjectMapper json;

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public TransactionResult execute(
      String key,
      String operation,
      Object body,
      Function<TransactionRecord, TransactionResult> work) {
    ApiException.require(
        key != null && !key.isBlank() && key.length() <= 128, "INVALID_IDEMPOTENCY_KEY");
    var actor = access.current();
    var lockedActor = users.lockById(actor.getId()).orElseThrow();
    entityManager.refresh(lockedActor, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    ApiException.require(lockedActor.getStatus() == UserStatus.ACTIVE, "UNAUTHORIZED");
    String hash = AuthService.hash(operation + "\n" + serialize(body));
    var existing = requests.findByUserIdAndIdempotencyKey(actor.getId(), key);
    if (existing.isPresent()) {
      var prior = existing.get();
      ApiException.require(prior.getRequestHash().equals(hash), "IDEMPOTENCY_CONFLICT");
      return deserialize(prior.getResultData());
    }
    var record = new TransactionRecord();
    record.setTransactionReference("TX" + UUID.randomUUID().toString().replace("-", ""));
    record.setUserId(actor.getId());
    record.setIdempotencyKey(key);
    record.setRequestHash(hash);
    record.setOperation(operation);
    record.setStatus(TransactionStatus.INITIATED);
    record = requests.saveAndFlush(record);
    var result = work.apply(record);
    record.setStatus(result.status());
    record.setResultData(serialize(result));
    return result;
  }

  public String serialize(Object data) {
    try {
      return json.writeValueAsString(data);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public TransactionResult deserialize(String data) {
    try {
      return json.readValue(data, TransactionResult.class);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
