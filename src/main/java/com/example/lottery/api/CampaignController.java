package com.example.lottery.api;

import com.example.lottery.service.CampaignService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.example.lottery.api.CampaignDtos.CampaignView;
import static com.example.lottery.api.CampaignDtos.SaveCampaignRequest;

@RestController
@RequestMapping("/api/v1/campaigns")
@Tag(name = "Campaigns", description = "Campaign discovery and administrator configuration")
@SecurityRequirement(name = "basicAuth")
public class CampaignController {
    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @GetMapping
    @Operation(summary = "List campaigns", description = "Returns each campaign and its current prize inventory.")
    public List<CampaignView> getCampaigns() {
        return campaignService.getCampaigns();
    }

    @GetMapping("/{campaignId}")
    @Operation(summary = "Get a campaign", description = "Returns campaign settings and current prize stock.")
    public CampaignView getCampaign(@PathVariable long campaignId) {
        return campaignService.getCampaign(campaignId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a campaign", description = "Admin only. All configured probabilities must total 100%.")
    public CampaignView createCampaign(@Valid @RequestBody SaveCampaignRequest request) {
        return campaignService.createCampaign(request);
    }

    @PutMapping("/{campaignId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replace campaign configuration", description = "Admin only. Replaces all prizes and campaign settings.")
    public CampaignView updateCampaign(
            @PathVariable long campaignId,
            @Valid @RequestBody SaveCampaignRequest request) {
        return campaignService.updateCampaign(campaignId, request);
    }
}
