package com.zhikao.server.controller;

import com.zhikao.server.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 参数校验演示接口（T2.2 验收用，验证非法参数返回 1001 且响应格式统一）。
 * 仅用于验证统一响应与全局异常处理，后续任务完成对应模块后移除。
 */
@Tag(name = "演示", description = "T2.2 参数校验演示（开发期临时）")
@RestController
@RequestMapping("/api/v1/demo")
@Validated
public class DemoController {

    @Operation(summary = "参数校验演示（@RequestParam）")
    @GetMapping("/validate")
    public Result<DemoResp> validate(
            @RequestParam @NotBlank(message = "不能为空") String name,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "不能小于 1")
            @Max(value = 50, message = "不能大于 50") int page) {
        return Result.ok(new DemoResp(name, page));
    }

    @Operation(summary = "参数校验演示（@RequestBody + @Valid）")
    @PostMapping("/validate-body")
    public Result<DemoResp> validateBody(@Valid @RequestBody DemoBody body) {
        return Result.ok(new DemoResp(body.getName(), body.getCount()));
    }

    @Data
    public static class DemoBody {
        @NotBlank(message = "不能为空")
        private String name;
        @Min(value = 1, message = "不能小于 1")
        @Max(value = 100, message = "不能大于 100")
        private int count;
    }

    @Data
    public static class DemoResp {
        private final String name;
        private final int page;
    }
}
