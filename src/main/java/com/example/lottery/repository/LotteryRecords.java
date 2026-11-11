package com.example.lottery.repository;

import java.util.List;

public final class LotteryRecords {
    private LotteryRecords() {
    }

    public record Campaign(
            long id,
            String name,
            int maxDrawsPerUser,
            int noWinProbabilityBasisPoints,
            boolean enabled) {
    }

    public record Prize(long id, String name, int stockRemaining, int probabilityBasisPoints) {
    }

    public record IdempotentRequest(long id, int requestedCount, String responseJson) {
    }

    public record CampaignWithPrizes(Campaign campaign, List<Prize> prizes) {
    }
}
