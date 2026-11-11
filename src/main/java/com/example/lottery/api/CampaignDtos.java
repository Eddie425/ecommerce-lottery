package com.example.lottery.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

public final class CampaignDtos {
    private CampaignDtos() {
    }

    @Schema(description = "Prize inventory and its probability for one campaign")
    public record PrizeRequest(
            @Schema(description = "Display name; must be unique within the campaign", example = "Coffee Voucher")
            @NotBlank @Size(max = 120) String name,
            @Schema(description = "Available inventory; zero is allowed", example = "20")
            @NotNull @Min(0) Integer stock,
            @Schema(description = "Probability as a percentage with up to two decimals", example = "15.00")
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00")
            @Digits(integer = 3, fraction = 2) BigDecimal probabilityPercent) {
    }

    @Schema(description = "Full campaign configuration. No-win and prize probabilities must total 100 percent.")
    public record SaveCampaignRequest(
            @Schema(description = "Campaign display name", example = "Summer Sale")
            @NotBlank @Size(max = 120) String name,
            @Schema(description = "Maximum total draws per authenticated user for this campaign", example = "10")
            @NotNull @Min(1) Integer maxDrawsPerUser,
            @Schema(description = "Probability of no prize as a percentage", example = "50.00")
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00")
            @Digits(integer = 3, fraction = 2) BigDecimal noWinProbabilityPercent,
            @Schema(description = "Whether this campaign currently accepts draws", example = "true")
            @NotNull Boolean enabled,
            @NotEmpty @Size(max = 100) List<@Valid PrizeRequest> prizes) {
    }

    public record PrizeView(long id, String name, int stockRemaining, BigDecimal probabilityPercent) {
    }

    public record CampaignView(
            long id,
            String name,
            int maxDrawsPerUser,
            boolean enabled,
            BigDecimal noWinProbabilityPercent,
            List<PrizeView> prizes) {
    }
}
