package com.std.chat.common.exception;

import com.std.chat.auth.exception.UnauthenticatedException;
import com.std.chat.common.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(UnauthenticatedException.class)
	public ResponseEntity<ErrorResponse> handleUnauthenticated(UnauthenticatedException ex) {
		return buildError(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage());
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
		return buildError(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
	}

	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
		return buildError(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
		return buildError(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
	}

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
		return buildError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.orElse("Validation failed");
		return buildError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
	}

	private ResponseEntity<ErrorResponse> buildError(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status)
				.body(ErrorResponse.builder().code(code).message(message).build());
	}

}
