package com.metax.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * CommonResult .
 *
 * <p>
 * 统一 API 返回壳
 *
 * @param <T> 响应数据类型
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "统一 API 返回壳")
public class CommonResult<T> {

    /**
     * 响应码
     */
    @Schema(description = "响应码", example = "200")
    private Integer code;

    /**
     * 响应消息
     */
    @Schema(description = "响应消息", example = "操作成功")
    private String message;

    /**
     * 响应数据
     */
    @Schema(description = "响应数据")
    private T data;

    /**
     * 响应时间戳
     */
    @Schema(description = "响应时间戳", example = "2026-06-03T11:30:00")
    private LocalDateTime timestamp;

    /**
     * 成功响应，无数据
     *
     * @param <T> 数据类型
     * @return 成功响应对象
     */
    public static <T> CommonResult<T> success() {
        return new CommonResult<>(200, "操作成功", null, LocalDateTime.now());
    }

    /**
     * 成功响应，带数据
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 成功响应对象
     */
    public static <T> CommonResult<T> success(T data) {
        return new CommonResult<>(200, "操作成功", data, LocalDateTime.now());
    }

    /**
     * 成功响应，自定义消息
     *
     * @param message 响应消息
     * @param data    响应数据
     * @param <T>     数据类型
     * @return 成功响应对象
     */
    public static <T> CommonResult<T> success(String message, T data) {
        return new CommonResult<>(200, message, data, LocalDateTime.now());
    }

    /**
     * 失败响应
     *
     * @param code    错误码
     * @param message 错误消息
     * @param <T>     数据类型
     * @return 失败响应对象
     */
    public static <T> CommonResult<T> error(Integer code, String message) {
        return new CommonResult<>(code, message, null, LocalDateTime.now());
    }
}
