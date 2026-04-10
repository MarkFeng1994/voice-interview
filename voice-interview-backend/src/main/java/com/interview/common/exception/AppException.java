package com.interview.common.exception;

import org.springframework.http.HttpStatus;

public class AppException extends RuntimeException {

	private final String code;
	private final HttpStatus httpStatus;

	public AppException(String code, HttpStatus httpStatus, String message) {
		super(message);
		this.code = code;
		this.httpStatus = httpStatus;
	}

	public AppException(String code, HttpStatus httpStatus, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
		this.httpStatus = httpStatus;
	}

	public String getCode() {
		return code;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	public static AppException badRequest(String code, String message) {
		return new AppException(code, HttpStatus.BAD_REQUEST, message);
	}

	public static AppException unauthorized(String code, String message) {
		return new AppException(code, HttpStatus.UNAUTHORIZED, message);
	}

	public static AppException forbidden(String code, String message) {
		return new AppException(code, HttpStatus.FORBIDDEN, message);
	}

	public static AppException notFound(String code, String message) {
		return new AppException(code, HttpStatus.NOT_FOUND, message);
	}

	public static AppException conflict(String code, String message) {
		return new AppException(code, HttpStatus.CONFLICT, message);
	}

	public static AppException serviceUnavailable(String code, String message, Throwable cause) {
		return new AppException(code, HttpStatus.SERVICE_UNAVAILABLE, message, cause);
	}
}
