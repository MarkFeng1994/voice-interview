package com.interview.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.interview.common.result.ApiResponse;
import com.interview.common.web.RequestTraceContext;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(AppException.class)
	public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
		log.warn("Application exception code={} requestId={} path={}", ex.getCode(), currentRequestId(), currentPath(), ex);
		return ResponseEntity.status(ex.getHttpStatus())
				.body(ApiResponse.error(ex.getCode(), ex.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
		log.warn("Bad request requestId={} path={}", currentRequestId(), currentPath(), ex);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.error("BAD_REQUEST", ex.getMessage()));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
		log.warn("Invalid state requestId={} path={}", currentRequestId(), currentPath(), ex);
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ApiResponse.error("INVALID_STATE", ex.getMessage()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
		log.warn("Malformed request body requestId={} path={}", currentRequestId(), currentPath(), ex);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.error("INVALID_BODY", "请求体格式不正确"));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		log.warn("Request parameter type mismatch requestId={} path={}", currentRequestId(), currentPath(), ex);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.error("INVALID_PARAM", "请求参数格式不正确"));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
		log.warn("Method not allowed requestId={} path={}", currentRequestId(), currentPath(), ex);
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
				.body(ApiResponse.error("METHOD_NOT_ALLOWED", "请求方法不支持"));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
		log.warn("Resource not found requestId={} path={}", currentRequestId(), currentPath(), ex);
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.error("NOT_FOUND", "请求资源不存在"));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
		log.error("Unexpected exception requestId={} path={}", currentRequestId(), currentPath(), ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiResponse.error("INTERNAL_ERROR", "服务内部异常，请稍后再试"));
	}

	private String currentRequestId() {
		HttpServletRequest request = currentRequest();
		if (request == null) {
			return "-";
		}
		Object value = request.getAttribute(RequestTraceContext.REQUEST_ID_ATTRIBUTE);
		return value == null ? "-" : String.valueOf(value);
	}

	private String currentPath() {
		HttpServletRequest request = currentRequest();
		return request == null ? "-" : request.getRequestURI();
	}

	private HttpServletRequest currentRequest() {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
			return null;
		}
		return attributes.getRequest();
	}
}
