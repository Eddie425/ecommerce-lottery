package com.example.lottery.api;

import com.example.lottery.service.DrawService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.example.lottery.api.DrawDtos.DrawRequest;
import static com.example.lottery.api.DrawDtos.DrawResponse;

@Validated
@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/draws")
@Tag(name = "Draws", description = "Single and consecutive campaign draws")
@SecurityRequirement(name = "basicAuth")
public class DrawController {
    private final DrawService drawService;

    public DrawController(DrawService drawService) {
        this.drawService = drawService;
    }

    @PostMapping
    @Operation(summary = "Draw prizes", description = "Retries with the same key and count return the original persisted result.")
    public DrawResponse draw(
            @Parameter(description = "Campaign identifier") @PathVariable long campaignId,
            @Parameter(description = "Client-generated key reused unchanged when retrying a request")
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody DrawRequest request,
            Authentication authentication) {
        return drawService.draw(campaignId, authentication.getName(), idempotencyKey, request.count());
    }
}
