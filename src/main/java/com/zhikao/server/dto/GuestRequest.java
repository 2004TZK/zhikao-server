package com.zhikao.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 游客换取 Token 请求（契约：6.1/6.2 POST /auth/guest）。
 */
@Data
public class GuestRequest {

    @NotBlank(message = "游客 UUID 不能为空")
    @Size(max = 36, message = "游客 UUID 最长 36 字符")
    private String guestUuid;
}
