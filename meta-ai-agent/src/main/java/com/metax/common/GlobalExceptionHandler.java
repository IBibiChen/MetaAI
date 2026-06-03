package com.metax.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Comparator;

/**
 * GlobalExceptionHandler .
 *
 * <p>
 * 统一 API 异常响应处理
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final int BAD_REQUEST = HttpStatus.BAD_REQUEST.value();

    private static final int INTERNAL_SERVER_ERROR = HttpStatus.INTERNAL_SERVER_ERROR.value();

    /**
     * RequestBody 参数校验异常
     *
     * @param ex 参数校验异常
     * @return 统一失败响应
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResult<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return CommonResult.error(BAD_REQUEST, fieldErrorMessage(ex.getBindingResult().getFieldErrors()));
    }

    /**
     * Query / form 参数绑定校验异常
     *
     * @param ex 参数绑定异常
     * @return 统一失败响应
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BindException.class)
    public CommonResult<Void> handleBind(BindException ex) {
        return CommonResult.error(BAD_REQUEST, fieldErrorMessage(ex.getBindingResult().getFieldErrors()));
    }

    /**
     * 方法参数校验异常
     *
     * @param ex 方法参数校验异常
     * @return 统一失败响应
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public CommonResult<Void> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        String message = ex.getParameterValidationResults()
                .stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : error.toString())
                .findFirst()
                .orElse("请求参数不合法");
        return CommonResult.error(BAD_REQUEST, message);
    }

    /**
     * ConstraintViolation 参数校验异常
     *
     * @param ex ConstraintViolation 参数校验异常
     * @return 统一失败响应
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public CommonResult<Void> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations()
                .stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("请求参数不合法");
        return CommonResult.error(BAD_REQUEST, message);
    }

    /**
     * 缺少请求参数异常
     *
     * @param ex 缺少请求参数异常
     * @return 统一失败响应
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public CommonResult<Void> handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
        return CommonResult.error(BAD_REQUEST, ex.getParameterName() + " 不能为空");
    }

    /**
     * 缺少 multipart 请求部分异常
     *
     * @param ex 缺少 multipart 请求部分异常
     * @return 统一失败响应
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MissingServletRequestPartException.class)
    public CommonResult<Void> handleMissingServletRequestPart(MissingServletRequestPartException ex) {
        return CommonResult.error(BAD_REQUEST, ex.getRequestPartName() + " 不能为空");
    }

    /**
     * multipart 上传异常
     *
     * @param ex 上传大小超限异常
     * @return 统一失败响应
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public CommonResult<Void> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return CommonResult.error(BAD_REQUEST, "上传文件大小不能超过 30MB");
    }

    /**
     * multipart 上传异常
     *
     * @param ex multipart 上传异常
     * @return 统一失败响应
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MultipartException.class)
    public CommonResult<Void> handleMultipart(MultipartException ex) {
        return CommonResult.error(BAD_REQUEST, ex.getMessage());
    }

    /**
     * 业务参数异常
     *
     * @param ex 业务参数异常
     * @return 统一失败响应
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public CommonResult<Void> handleIllegalArgument(IllegalArgumentException ex) {
        return CommonResult.error(BAD_REQUEST, ex.getMessage());
    }

    /**
     * 业务状态异常
     *
     * @param ex 业务状态异常
     * @return 统一失败响应
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(IllegalStateException.class)
    public CommonResult<Void> handleIllegalState(IllegalStateException ex) {
        return CommonResult.error(INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    /**
     * 未预期异常
     *
     * @param ex 未预期异常
     * @return 统一失败响应
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public CommonResult<Void> handleException(Exception ex) {
        log.error("接口处理发生未预期异常", ex);
        return CommonResult.error(INTERNAL_SERVER_ERROR, "系统异常");
    }

    private String fieldErrorMessage(java.util.List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .min(Comparator.comparing(FieldError::getField))
                .map(error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : error.getField() + " 不合法")
                .orElse("请求参数不合法");
    }
}
