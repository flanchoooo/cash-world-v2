package com.poscloud.wallet.commission;

import com.poscloud.wallet.biller.BillerProduct;
import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class RewardService {
  public BigDecimal calculate(
      BillerProduct product, BigDecimal faceValue, int places, boolean agent) {
    if (!agent || product.getAgentRewardMode() == RewardMode.NONE) return BigDecimal.ZERO;
    var reward =
        product.getAgentRewardType() == RewardType.FIXED
            ? product.getAgentRewardValue()
            : faceValue.multiply(product.getAgentRewardValue()).movePointLeft(2);
    if (product.getMinimumCommission() != null) reward = reward.max(product.getMinimumCommission());
    if (product.getMaximumCommission() != null) reward = reward.min(product.getMaximumCommission());
    reward = Money.round(reward, places);
    ApiException.require(
        reward.signum() >= 0 && reward.compareTo(faceValue) < 0, "INVALID_COMMISSION");
    return reward;
  }
}
