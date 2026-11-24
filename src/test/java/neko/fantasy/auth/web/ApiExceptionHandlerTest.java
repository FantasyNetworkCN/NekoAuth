package neko.fantasy.auth.web;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void reportsDataTooLongAsBadRequestInsteadOfConflict() {
        var exception = new DataIntegrityViolationException(
                "insert failed",
                new SQLException("Data too long for column 'device_info'", "22001", 1406));

        var response = handler.handleDataIntegrityViolation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("DATA_TOO_LONG");
        assertThat(response.getBody().message()).isEqualTo("提交的数据过长");
    }

    @Test
    void keepsDuplicateKeyAsConflict() {
        var exception = new DataIntegrityViolationException(
                "insert failed",
                new SQLException("Duplicate entry", "23000", 1062));

        var response = handler.handleDataIntegrityViolation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CONFLICT");
    }
}
