package com.example.lottery.service;

import com.example.lottery.repository.LotteryRecords.Prize;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProbabilitySelectorTest {
    private final ProbabilitySelector selector = new ProbabilitySelector();
    private final Prize first = new Prize(1, "First", 1, 500);
    private final Prize second = new Prize(2, "Second", 2, 1_500);

    @Test
    void selectsNoPrizeAtNoWinBoundary() {
        assertThat(selector.selectForRoll(0, 5_000, List.of(first, second))).isNull();
        assertThat(selector.selectForRoll(4_999, 5_000, List.of(first, second))).isNull();
    }

    @Test
    void selectsPrizeAtEachInclusiveStartAndExclusiveEndBoundary() {
        assertThat(selector.selectForRoll(5_000, 5_000, List.of(first, second))).isEqualTo(first);
        assertThat(selector.selectForRoll(5_499, 5_000, List.of(first, second))).isEqualTo(first);
        assertThat(selector.selectForRoll(5_500, 5_000, List.of(first, second))).isEqualTo(second);
        assertThat(selector.selectForRoll(6_999, 5_000, List.of(first, second))).isEqualTo(second);
    }

    @Test
    void rejectsRollOutsideOneHundredPercentRange() {
        assertThatThrownBy(() -> selector.selectForRoll(10_000, 5_000, List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void producesExactConfiguredFrequencyAcrossEveryBasisPoint() {
        int[] outcomes = new int[3];
        for (int roll = 0; roll < 10_000; roll++) {
            Prize selected = selector.selectForRoll(roll, 8_000, List.of(first, second));
            if (selected == null) {
                outcomes[0]++;
            } else if (selected.equals(first)) {
                outcomes[1]++;
            } else {
                outcomes[2]++;
            }
        }
        assertThat(outcomes).containsExactly(8_000, 500, 1_500);
    }
}
