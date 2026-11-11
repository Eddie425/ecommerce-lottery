package com.example.lottery.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public final class DrawDtos {
    private DrawDtos() {
    }

    public enum DrawOutcome {
        WON,
        NO_PRIZE,
        SOLD_OUT
    }

    public record DrawRequest(
            @Schema(description = "Number of consecutive draws in this atomic request (1 to 100)", example = "3")
            @Min(1) @Max(100) int count) {
    }

    @Schema(description = "One prize, no-prize, or sold-out outcome")
    public record DrawResult(
            int drawNumber,
            DrawOutcome outcome,
            String prizeName,
            String message,
            Instant drawnAt) {
    }

    @Schema(description = "Persisted results for a draw request; retries with the same idempotency key return this response")
    public record DrawResponse(
            long campaignId,
            int count,
            int remainingDraws,
            List<DrawResult> results) {
    }
}
