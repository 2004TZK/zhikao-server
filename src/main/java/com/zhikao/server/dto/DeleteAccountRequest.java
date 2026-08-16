package com.zhikao.server.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 账号注销请求（契约：6.2 POST /auth/delete-account，需二次确认凭证=密码）。
 */
@Data
public class DeleteAccountRequest {

    @NotBlank(message = "密码不能为空")
    private String password;
}
