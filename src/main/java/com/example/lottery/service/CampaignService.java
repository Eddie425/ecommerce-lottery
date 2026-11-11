package com.example.lottery.service;

import com.example.lottery.api.CampaignDtos.CampaignView;
import com.example.lottery.api.CampaignDtos.PrizeView;
import com.example.lottery.api.CampaignDtos.SaveCampaignRequest;
import com.example.lottery.repository.CampaignRepository;
import com.example.lottery.repository.LotteryRecords.Campaign;
import com.example.lottery.repository.LotteryRecords.Prize;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CampaignService {
    private static final int TOTAL_BASIS_POINTS = 10_000;

    private final CampaignRepository campaignRepository;

    public CampaignService(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    @Transactional(readOnly = true)
    public List<CampaignView> getCampaigns() {
        return campaignRepository.findAll().stream()
                .map(campaign -> toView(campaign, campaignRepository.findPrizes(campaign.id())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CampaignView getCampaign(long campaignId) {
        Campaign campaign = findCampaign(campaignId);
        return toView(campaign, campaignRepository.findPrizes(campaignId));
    }

    @Transactional
    public CampaignView createCampaign(SaveCampaignRequest request) {
        int noWin = validateAndConvert(request);
        long campaignId = campaignRepository.insert(request, noWin, prizeProbabilities(request));
        return getCampaign(campaignId);
    }

    @Transactional
    public CampaignView updateCampaign(long campaignId, SaveCampaignRequest request) {
        int noWin = validateAndConvert(request);
        Campaign campaign = campaignRepository.lockCampaignForUpdate(campaignId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Campaign not found"));
        campaignRepository.update(campaign.id(), request, noWin, prizeProbabilities(request));
        return getCampaign(campaignId);
    }

    public static int toBasisPoints(BigDecimal percent) {
        try {
            int basisPoints = percent.movePointRight(2).intValueExact();
            if (basisPoints < 0 || basisPoints > TOTAL_BASIS_POINTS) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Probabilities must be between 0 and 100 percent");
            }
            return basisPoints;
        } catch (ArithmeticException exception) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Probabilities support at most two decimal places");
        }
    }

    int validateAndConvert(SaveCampaignRequest request) {
        int noWin = toBasisPoints(request.noWinProbabilityPercent());
        long total = noWin;
        for (var prize : request.prizes()) {
            total += toBasisPoints(prize.probabilityPercent());
        }
        if (total != TOTAL_BASIS_POINTS) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "No-win and prize probabilities must sum to exactly 100.00 percent");
        }
        long uniqueNames = request.prizes().stream()
                .map(prize -> prize.name().trim().toLowerCase(java.util.Locale.ROOT))
                .distinct()
                .count();
        if (uniqueNames != request.prizes().size()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Prize names must be unique within a campaign");
        }
        return noWin;
    }

    private Campaign findCampaign(long campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Campaign not found"));
    }

    private List<Integer> prizeProbabilities(SaveCampaignRequest request) {
        return request.prizes().stream()
                .map(prize -> toBasisPoints(prize.probabilityPercent()))
                .toList();
    }

    private CampaignView toView(Campaign campaign, List<Prize> prizes) {
        return new CampaignView(
                campaign.id(),
                campaign.name(),
                campaign.maxDrawsPerUser(),
                campaign.enabled(),
                BigDecimal.valueOf(campaign.noWinProbabilityBasisPoints(), 2),
                prizes.stream()
                        .map(prize -> new PrizeView(
                                prize.id(),
                                prize.name(),
                                prize.stockRemaining(),
                                BigDecimal.valueOf(prize.probabilityBasisPoints(), 2)))
                        .toList());
    }
}
