package com.example.lottery.service;

import com.example.lottery.api.CampaignDtos.CampaignView;
import com.example.lottery.api.CampaignDtos.PrizeRequest;
import com.example.lottery.api.CampaignDtos.SaveCampaignRequest;
import com.example.lottery.api.DrawDtos.DrawResponse;
import com.example.lottery.api.DrawDtos.DrawOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class DrawServiceIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private DrawService drawService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute(
                "TRUNCATE draw_history, draw_requests, user_campaign_stats, campaign_prizes, campaigns "
                        + "RESTART IDENTITY CASCADE");
    }

    @Test
    void replaysIdempotentRequestAndDoesNotExceedQuotaOrStock() {
        long campaignId = createCampaign(1, 1, "100.00", "0.00");

        DrawResponse first = drawService.draw(campaignId, "alice", "same-request", 1);
        DrawResponse replay = drawService.draw(campaignId, "alice", "same-request", 1);

        assertThat(first).isEqualTo(replay);
        assertThat(first.results()).singleElement().extracting(result -> result.outcome()).isEqualTo(DrawOutcome.WON);
        assertThat(first.remainingDraws()).isZero();
        assertThatThrownBy(() -> drawService.draw(campaignId, "alice", "another-request", 1))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("draw limit");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM draw_requests", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT stock_remaining FROM campaign_prizes WHERE campaign_id = ?",
                Integer.class,
                campaignId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM draw_history",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsIdempotencyKeyReuseWithDifferentPayload() {
        long campaignId = createCampaign(10, 10, "100.00", "0.00");
        drawService.draw(campaignId, "alice", "fixed-key", 1);

        assertThatThrownBy(() -> drawService.draw(campaignId, "alice", "fixed-key", 2))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("different draw count");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM draw_history", Integer.class)).isEqualTo(1);
    }

    @Test
    void replaysPriorSuccessAfterCampaignIsDisabled() {
        long campaignId = createCampaign(10, 10, "100.00", "0.00");
        DrawResponse original = drawService.draw(campaignId, "alice", "saved-result", 1);
        campaignService.updateCampaign(campaignId, new SaveCampaignRequest(
                "Test Campaign",
                10,
                new BigDecimal("0.00"),
                false,
                List.of(new PrizeRequest("Test Prize", 0, new BigDecimal("100.00")))));

        assertThat(drawService.draw(campaignId, "alice", "saved-result", 1)).isEqualTo(original);
    }

    @Test
    void concurrentRetriesWithSameKeyReturnTheSameCommittedDraw() throws Exception {
        long campaignId = createCampaign(1, 1, "100.00", "0.00");
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<DrawResponse> first = executor.submit(
                    () -> drawService.draw(campaignId, "alice", "concurrent-key", 1));
            Future<DrawResponse> second = executor.submit(
                    () -> drawService.draw(campaignId, "alice", "concurrent-key", 1));

            assertThat(first.get()).isEqualTo(second.get());
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM draw_history", Integer.class)).isEqualTo(1);
        }
    }

    @Test
    void concurrentDrawsNeverOversellPrizeInventory() throws Exception {
        long campaignId = createCampaign(100, 5, "100.00", "0.00");
        try (var executor = Executors.newFixedThreadPool(12)) {
            List<Future<DrawResponse>> futures = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> executor.submit(
                            () -> drawService.draw(campaignId, "user-" + index, "key-" + index, 1)))
                    .toList();
            List<DrawResponse> responses = new java.util.ArrayList<>();
            for (Future<DrawResponse> future : futures) {
                responses.add(future.get());
            }

            long wins = responses.stream()
                    .flatMap(response -> response.results().stream())
                    .filter(result -> result.outcome() == DrawOutcome.WON)
                    .count();
            long soldOut = responses.stream()
                    .flatMap(response -> response.results().stream())
                    .filter(result -> result.outcome() == DrawOutcome.SOLD_OUT)
                    .count();
            assertThat(wins).isEqualTo(5);
            assertThat(soldOut).isEqualTo(15);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT stock_remaining FROM campaign_prizes WHERE campaign_id = ?",
                    Integer.class,
                    campaignId)).isZero();
        }
    }

    private long createCampaign(int maxDraws, int stock, String prizeProbability, String noWinProbability) {
        CampaignView campaign = campaignService.createCampaign(new SaveCampaignRequest(
                "Test Campaign",
                maxDraws,
                new BigDecimal(noWinProbability),
                true,
                List.of(new PrizeRequest("Test Prize", stock, new BigDecimal(prizeProbability)))));
        return campaign.id();
    }
}
