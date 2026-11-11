package com.example.lottery.service;

import com.example.lottery.api.CampaignDtos.PrizeRequest;
import com.example.lottery.api.CampaignDtos.SaveCampaignRequest;
import com.example.lottery.repository.CampaignRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CampaignServiceTest {
    private final CampaignService service = new CampaignService(mock(CampaignRepository.class));

    @Test
    void convertsPercentToIntegerBasisPointsExactly() {
        assertThat(CampaignService.toBasisPoints(new BigDecimal("12.34"))).isEqualTo(1_234);
    }

    @Test
    void rejectsMoreThanTwoProbabilityDecimalPlaces() {
        assertThatThrownBy(() -> CampaignService.toBasisPoints(new BigDecimal("12.345")))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void validatesNoWinAndPrizeProbabilitiesSumToOneHundredPercent() {
        var valid = request("50.00", new PrizeRequest("Prize A", 1, new BigDecimal("25.00")),
                new PrizeRequest("Prize B", 2, new BigDecimal("25.00")));
        assertThat(service.validateAndConvert(valid)).isEqualTo(5_000);

        var invalid = request("50.00", new PrizeRequest("Prize A", 1, new BigDecimal("49.99")));
        assertThatThrownBy(() -> service.validateAndConvert(invalid))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("sum to exactly 100.00 percent");
    }

    private SaveCampaignRequest request(String noWin, PrizeRequest... prizes) {
        return new SaveCampaignRequest("Test", 10, new BigDecimal(noWin), true, List.of(prizes));
    }
}
