package neko.fantasy.auth.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class DypnsSmsClientTest {

    @ParameterizedTest
    @ValueSource(strings = {"biz.FREQUENCY", "FREQUENCY_FAIL", "isv.BUSINESS_LIMIT_CONTROL", "BUSINESS_LIMIT_CONTROL"})
    void mapsProviderFrequencyErrorsToTooManyRequests(String providerCode) {
        var failure = DypnsSmsClient.sendFailure(providerCode);

        assertThat(failure.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(failure.getCode()).isEqualTo("SMS_CODE_RATE_LIMIT");
    }
}
