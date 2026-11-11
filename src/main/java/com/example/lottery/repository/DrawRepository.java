package com.example.lottery.repository;

import com.example.lottery.repository.LotteryRecords.IdempotentRequest;
import com.example.lottery.repository.LotteryRecords.Prize;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class DrawRepository {
    private static final RowMapper<IdempotentRequest> REQUEST_MAPPER = (rs, rowNum) -> new IdempotentRequest(
            rs.getLong("id"),
            rs.getInt("requested_count"),
            rs.getString("response_json"));

    private final JdbcTemplate jdbcTemplate;

    public DrawRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean insertRequest(long campaignId, String userId, String key, int count) {
        return jdbcTemplate.update(
                """
                INSERT INTO draw_requests (campaign_id, user_id, idempotency_key, requested_count)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (campaign_id, user_id, idempotency_key) DO NOTHING
                """,
                campaignId,
                userId,
                key,
                count) == 1;
    }

    public IdempotentRequest findRequest(long campaignId, String userId, String key) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id, requested_count, response_json::text AS response_json
                FROM draw_requests
                WHERE campaign_id = ? AND user_id = ? AND idempotency_key = ?
                FOR UPDATE
                """,
                REQUEST_MAPPER,
                campaignId,
                userId,
                key);
    }

    public Optional<Integer> reserveDraws(long campaignId, String userId, int count, int maxDraws) {
        try {
            Integer drawsUsed = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO user_campaign_stats (campaign_id, user_id, draws_used)
                    VALUES (?, ?, ?)
                    ON CONFLICT (campaign_id, user_id) DO UPDATE
                    SET draws_used = user_campaign_stats.draws_used + EXCLUDED.draws_used
                    WHERE user_campaign_stats.draws_used + EXCLUDED.draws_used <= ?
                    RETURNING draws_used
                    """,
                    Integer.class,
                    campaignId,
                    userId,
                    count,
                    maxDraws);
            return Optional.of(drawsUsed);
        } catch (EmptyResultDataAccessException exceededQuota) {
            return Optional.empty();
        }
    }

    public boolean decrementStock(long prizeId) {
        return jdbcTemplate.update(
                "UPDATE campaign_prizes SET stock_remaining = stock_remaining - 1 "
                        + "WHERE id = ? AND stock_remaining > 0",
                prizeId) == 1;
    }

    public void insertHistory(long requestId, int drawNumber, String outcome, String prizeName) {
        jdbcTemplate.update(
                """
                INSERT INTO draw_history (draw_request_id, draw_number, outcome, prize_name)
                VALUES (?, ?, ?, ?)
                """,
                requestId,
                drawNumber,
                outcome,
                prizeName);
    }

    public void completeRequest(long requestId, String responseJson) {
        jdbcTemplate.update(
                "UPDATE draw_requests SET response_json = ?::jsonb WHERE id = ?",
                responseJson,
                requestId);
    }

}
