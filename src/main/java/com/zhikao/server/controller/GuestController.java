package com.zhikao.server.controller;

import com.zhikao.server.common.Result;
import com.zhikao.server.security.AuthContext;
import com.zhikao.server.service.GuestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 游客额度（T4.3，契约 6.1 游客约定）。
 * 服务端强制校验：知识点/成语详情浏览各 5 次、答题 5 道；超限返回 9001。
 * 详情浏览的额度消耗由 T5.1/T5.2 的详情接口调用 GuestService 完成（此处提供查询与测试入口）。
 */
@Tag(name = "游客", description = "游客额度查询（T4.3）")
@RestController
@RequestMapping("/api/v1/guest")
@RequiredArgsConstructor
public class GuestController {

    private final GuestService guestService;

    @Operation(summary = "查询游客当日剩余额度")
    @GetMapping("/quota")
    public Result<Map<String, Integer>> quota() {
        Long userId = AuthContext.getUserId();
        Map<String, Integer> map = new HashMap<>();
        map.put("knowledgeDetail", guestService.remainingQuota(userId, "detail_knowledge"));
        map.put("idiomDetail", guestService.remainingQuota(userId, "detail_idiom"));
        map.put("answer", guestService.remainingQuota(userId, "answer"));
        return Result.ok(map);
    }

    @Operation(summary = "消耗一次详情浏览额度（开发期验证用，正式接入点在 T5 详情接口）")
    @GetMapping("/consume-detail")
    public Result<Map<String, Integer>> consumeDetail(@RequestParam(defaultValue = "knowledge") String type) {
        Long userId = AuthContext.getUserId();
        String quotaType = "knowledge".equals(type) ? "detail_knowledge" : "detail_idiom";
        guestService.consumeQuota(userId, quotaType);
        Map<String, Integer> map = new HashMap<>();
        map.put("remaining", guestService.remainingQuota(userId, quotaType));
        return Result.ok(map);
    }
}
