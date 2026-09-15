package com.tasklean.api.common.exception;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.common.ErrorMessages;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring's default Whitelabel error page. Any error dispatched to {@code /error}
 * that wasn't already handled by {@link GlobalExceptionHandler} (e.g. a wrong HTTP method,
 * or an error raised in a filter before MVC) returns the JSON {@link ApiResponse} envelope
 * instead of HTML — so API clients always get JSON, whatever their Accept header.
 */
@RestController
public class ApiErrorController implements ErrorController {

    /**
     * Renders any forwarded error as an {@link ApiResponse}, mapping the servlet status to a
     * safe generic message (no internal detail is exposed).
     *
     * @param request the errored request, carrying the original status as a dispatch attribute
     * @return the error as a JSON {@link ApiResponse} with the original status code
     */
    @RequestMapping(value = "/error", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<ApiResponse<Void>> handleError(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request);
        String message = switch (status) {
            case NOT_FOUND -> ErrorMessages.ENDPOINT_NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ErrorMessages.METHOD_NOT_ALLOWED;
            case FORBIDDEN -> ErrorMessages.ACCESS_DENIED;
            case UNAUTHORIZED -> ErrorMessages.AUTHENTICATION_REQUIRED;
            default -> ErrorMessages.INTERNAL_ERROR;
        };
        return ResponseEntity.status(status).body(ApiResponse.error(message));
    }

    private HttpStatus resolveStatus(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (code == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        HttpStatus resolved = HttpStatus.resolve(Integer.parseInt(code.toString()));
        return resolved != null ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
