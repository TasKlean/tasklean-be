package com.tasklean.api.common;

import lombok.*;

/**
 * Standard response envelope wrapping every API result with a {@code success} flag,
 * an optional {@code message}, and the payload {@code data}. Construct via the static
 * factory methods rather than the builder directly.
 *
 * @param <T> the payload type
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    /**
     * Builds a successful response carrying only a payload.
     *
     * @param data the payload
     * @param <T>  the payload type
     * @return a success envelope with the given data
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    /**
     * Builds a successful response carrying a message and a payload.
     *
     * @param message a human-readable message
     * @param data    the payload
     * @param <T>     the payload type
     * @return a success envelope with the given message and data
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * Builds an error response carrying a message and no payload.
     *
     * @param message the error message
     * @param <T>     the payload type
     * @return an error envelope with the given message
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}
