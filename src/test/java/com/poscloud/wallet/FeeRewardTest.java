package com.poscloud.wallet;

import static org.assertj.core.api.Assertions.*;

import com.poscloud.wallet.biller.BillerProduct;
import com.poscloud.wallet.commission.RewardService;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.fee.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FeeRewardTest {
  @Test
  void twoPercentReward() {
    var p = new BillerProduct();
    p.setAgentRewardMode(RewardMode.DISCOUNT);
    p.setAgentRewardType(RewardType.PERCENTAGE);
    p.setAgentRewardValue(new BigDecimal("2"));
    assertThat(new RewardService().calculate(p, new BigDecimal("20"), 2, true))
        .isEqualByComparingTo("0.40");
    assertThat(new RewardService().calculate(p, new BigDecimal("20"), 2, false)).isZero();
  }

  @Test
  void feeClampsAndRounds() {
    var f = new Fee();
    f.setCalculationType(CalculationType.FIXED_PLUS_PERCENTAGE);
    f.setFixedAmount(new BigDecimal("0.20"));
    f.setPercentage(new BigDecimal("2"));
    f.setMinimumFee(new BigDecimal("0.50"));
    f.setMaximumFee(new BigDecimal("5"));
    var service = new FeeService(null, null);
    assertThat(service.compute(f, BigDecimal.ONE, 2)).isEqualByComparingTo("0.50");
    assertThat(service.compute(f, new BigDecimal("20"), 2)).isEqualByComparingTo("0.60");
    assertThat(service.compute(f, new BigDecimal("1000"), 2)).isEqualByComparingTo("5");
  }
}
