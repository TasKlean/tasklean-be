package com.tasklean.api.common.exception;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.common.ErrorMessages;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorControllerTest {

    private final ApiErrorController controller = new ApiErrorController();

    private ResponseEntity<ApiResponse<Void>> errorFor(Integer status) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (status != null) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        }
        return controller.handleError(request);
    }

    @Test
    void handleError_notFound_returns404Json() {
        ResponseEntity<ApiResponse<Void>> response = errorFor(404);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiResponse::isSuccess, ApiResponse::getMessage)
                .containsExactly(false, ErrorMessages.ENDPOINT_NOT_FOUND);
    }

    @Test
    void handleError_methodNotAllowed_returns405Json() {
        ResponseEntity<ApiResponse<Void>> response = errorFor(405);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiResponse::isSuccess, ApiResponse::getMessage)
                .containsExactly(false, ErrorMessages.METHOD_NOT_ALLOWED);
    }

    @Test
    void handleError_forbidden_returns403Json() {
        ResponseEntity<ApiResponse<Void>> response = errorFor(403);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiResponse::isSuccess, ApiResponse::getMessage)
                .containsExactly(false, ErrorMessages.ACCESS_DENIED);
    }

    @Test
    void handleError_noStatusAttribute_returns500Json() {
        ResponseEntity<ApiResponse<Void>> response = errorFor(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiResponse::isSuccess, ApiResponse::getMessage)
                .containsExactly(false, ErrorMessages.INTERNAL_ERROR);
    }
}
