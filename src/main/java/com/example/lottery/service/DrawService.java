package com.example.lottery.service;

import com.example.lottery.api.DrawDtos.DrawResponse;
import com.example.lottery.api.DrawDtos.DrawResult;
import com.example.lottery.api.DrawDtos.DrawOutcome;
import com.example.lottery.repository.CampaignRepository;
import com.example.lottery.repository.DrawRepository;
import com.example.lottery.repository.LotteryRecords.Campaign;
import com.example.lottery.repository.LotteryRecords.IdempotentRequest;
import com.example.lottery.repository.LotteryRecords.Prize;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class DrawService {
    private final CampaignRepository campaignRepository;
    private final DrawRepository drawRepository;
    private final ProbabilitySelector probabilitySelector;
    private final ObjectMapper objectMapper;

    public DrawService(
            CampaignRepository campaignRepository,
            DrawRepository drawRepository,
            ProbabilitySelector probabilitySelector,
            ObjectMapper objectMapper) {
        this.campaignRepository = campaignRepository;
        this.drawRepository = drawRepository;
        this.probabilitySelector = probabilitySelector;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DrawResponse draw(long campaignId, String userId, String idempotencyKey, int count) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Idempotency-Key must contain 1 to 128 characters");
        }
        if (count < 1 || count > 100) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Draw count must be between 1 and 100");
        }

        Campaign campaign = campaignRepository.findByIdForShare(campaignId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Campaign not found"));

        boolean created = drawRepository.insertRequest(campaignId, userId, idempotencyKey, count);
        IdempotentRequest request = drawRepository.findRequest(campaignId, userId, idempotencyKey);
        if (!created) {
            if (request.requestedCount() != count) {
                throw new AppException(HttpStatus.CONFLICT,
                        "Idempotency-Key was already used with a different draw count");
            }
            if (request.responseJson() == null) {
                throw new AppException(HttpStatus.CONFLICT, "The original request is still being processed");
            }
            return readResponse(request.responseJson());
        }

        if (!campaign.enabled()) {
            throw new AppException(HttpStatus.CONFLICT, "Campaign is disabled");
        }

        int drawsUsed = drawRepository.reserveDraws(
                        campaignId, userId, count, campaign.maxDrawsPerUser())
                .orElseThrow(() -> new AppException(HttpStatus.TOO_MANY_REQUESTS,
                        "The user's draw limit for this campaign would be exceeded"));
        List<Prize> prizes = campaignRepository.findPrizes(campaignId);
        List<DrawResult> results = new ArrayList<>(count);

        for (int drawNumber = 1; drawNumber <= count; drawNumber++) {
            Prize selectedPrize = probabilitySelector.select(campaign.noWinProbabilityBasisPoints(), prizes);
            DrawResult result;
            if (selectedPrize == null) {
                result = result(drawNumber, DrawOutcome.NO_PRIZE, null, "Thanks for participating");
            } else if (drawRepository.decrementStock(selectedPrize.id())) {
                result = result(drawNumber, DrawOutcome.WON, selectedPrize.name(), "You won a prize");
            } else {
                result = result(drawNumber, DrawOutcome.SOLD_OUT, null, "The selected prize is sold out");
            }
            drawRepository.insertHistory(
                    request.id(), drawNumber, result.outcome().name(), result.prizeName());
            results.add(result);
        }

        DrawResponse response = new DrawResponse(
                campaignId,
                count,
                Math.max(campaign.maxDrawsPerUser() - drawsUsed, 0),
                List.copyOf(results));
        drawRepository.completeRequest(request.id(), writeResponse(response));
        return response;
    }

    private DrawResult result(int drawNumber, DrawOutcome outcome, String prizeName, String message) {
        return new DrawResult(drawNumber, outcome, prizeName, message, Instant.now());
    }

    private String writeResponse(DrawResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize draw response", exception);
        }
    }

    private DrawResponse readResponse(String responseJson) {
        try {
            return objectMapper.readValue(responseJson, DrawResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize stored draw response", exception);
        }
    }
}
