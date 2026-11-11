package com.example.lottery.service;

import com.example.lottery.repository.LotteryRecords.Prize;
import org.springframework.stereotype.Component;

import java.util.List;
import java.security.SecureRandom;

@Component
public class ProbabilitySelector {
    private final SecureRandom random = new SecureRandom();

    public Prize select(int noWinBasisPoints, List<Prize> prizes) {
        return selectForRoll(random.nextInt(10_000), noWinBasisPoints, prizes);
    }

    Prize selectForRoll(int roll, int noWinBasisPoints, List<Prize> prizes) {
        if (roll < 0 || roll >= 10_000) {
            throw new IllegalArgumentException("Roll must be between 0 and 9999");
        }
        if (roll < noWinBasisPoints) {
            return null;
        }

        int prizeRoll = roll - noWinBasisPoints;
        for (Prize prize : prizes) {
            if (prizeRoll < prize.probabilityBasisPoints()) {
                return prize;
            }
            prizeRoll -= prize.probabilityBasisPoints();
        }
        throw new IllegalStateException("Configured probabilities do not sum to 10000 basis points");
    }
}
