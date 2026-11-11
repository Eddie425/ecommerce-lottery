package com.example.lottery.repository;

import com.example.lottery.api.CampaignDtos.PrizeRequest;
import com.example.lottery.api.CampaignDtos.SaveCampaignRequest;
import com.example.lottery.repository.LotteryRecords.Campaign;
import com.example.lottery.repository.LotteryRecords.Prize;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CampaignRepository {
    private static final RowMapper<Campaign> CAMPAIGN_MAPPER = (rs, rowNum) -> new Campaign(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getInt("max_draws_per_user"),
            rs.getInt("no_win_probability_basis_points"),
            rs.getBoolean("enabled"));
    private static final RowMapper<Prize> PRIZE_MAPPER = (rs, rowNum) -> new Prize(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getInt("stock_remaining"),
            rs.getInt("probability_basis_points"));

    private final JdbcTemplate jdbcTemplate;

    public CampaignRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Campaign> findAll() {
        return jdbcTemplate.query("SELECT * FROM campaigns ORDER BY id", CAMPAIGN_MAPPER);
    }

    public Optional<Campaign> findById(long campaignId) {
        return findById(campaignId, false);
    }

    public Optional<Campaign> findByIdForShare(long campaignId) {
        return findById(campaignId, true);
    }

    private Optional<Campaign> findById(long campaignId, boolean lockForShare) {
        String sql = "SELECT * FROM campaigns WHERE id = ?" + (lockForShare ? " FOR SHARE" : "");
        return jdbcTemplate.query(sql, CAMPAIGN_MAPPER, campaignId).stream().findFirst();
    }

    public List<Prize> findPrizes(long campaignId) {
        return jdbcTemplate.query(
                "SELECT * FROM campaign_prizes WHERE campaign_id = ? ORDER BY id", PRIZE_MAPPER, campaignId);
    }

    public long insert(
            SaveCampaignRequest request, int noWinBasisPoints, List<Integer> prizeProbabilityBasisPoints) {
        Long campaignId = jdbcTemplate.queryForObject(
                """
                INSERT INTO campaigns (name, max_draws_per_user, no_win_probability_basis_points, enabled)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                request.name().trim(),
                request.maxDrawsPerUser(),
                noWinBasisPoints,
                request.enabled());
        insertPrizes(campaignId, request.prizes(), prizeProbabilityBasisPoints);
        return campaignId;
    }

    public void update(
            long campaignId,
            SaveCampaignRequest request,
            int noWinBasisPoints,
            List<Integer> prizeProbabilityBasisPoints) {
        jdbcTemplate.update(
                """
                UPDATE campaigns
                SET name = ?, max_draws_per_user = ?, no_win_probability_basis_points = ?,
                    enabled = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                request.name().trim(),
                request.maxDrawsPerUser(),
                noWinBasisPoints,
                request.enabled(),
                campaignId);
        jdbcTemplate.update("DELETE FROM campaign_prizes WHERE campaign_id = ?", campaignId);
        insertPrizes(campaignId, request.prizes(), prizeProbabilityBasisPoints);
    }

    public Optional<Campaign> lockCampaignForUpdate(long campaignId) {
        return jdbcTemplate.query(
                "SELECT * FROM campaigns WHERE id = ? FOR UPDATE", CAMPAIGN_MAPPER, campaignId)
                .stream()
                .findFirst();
    }

    private void insertPrizes(
            long campaignId, List<PrizeRequest> prizes, List<Integer> prizeProbabilityBasisPoints) {
        for (int index = 0; index < prizes.size(); index++) {
            PrizeRequest prize = prizes.get(index);
            jdbcTemplate.update(
                    """
                    INSERT INTO campaign_prizes
                        (campaign_id, name, stock_remaining, probability_basis_points)
                    VALUES (?, ?, ?, ?)
                    """,
                    campaignId,
                    prize.name().trim(),
                    prize.stock(),
                    prizeProbabilityBasisPoints.get(index));
        }
    }
}
