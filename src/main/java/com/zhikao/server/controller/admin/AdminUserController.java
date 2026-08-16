package com.zhikao.server.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.server.common.BizException;
import com.zhikao.server.common.ErrorCode;
import com.zhikao.server.common.Result;
import com.zhikao.server.entity.User;
import com.zhikao.server.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理后台-用户管理（契约：6.11 /admin/user，T8.4）。
 */
@Tag(name = "管理后台-用户")
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "用户列表（支持搜索/禁用状态过滤）")
    @GetMapping
    public Result<Page<User>> list(@RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int size,
                                   @RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword));
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getCreatedAt);
        return Result.ok(userMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @Operation(summary = "禁用/启用用户（禁用后登录返回 2003）")
    @PostMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        return Result.ok();
    }

    @Operation(summary = "学习数据概览")
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> result = new HashMap<>();
        result.put("totalUsers", userMapper.selectCount(null));
        result.put("activeUsers", userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 0)));
        Long answers = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_answer", Long.class);
        Long wrongs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wrong_question WHERE is_removed = 0", Long.class);
        Long reviews = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM review_record", Long.class);
        Long studyMinutes = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(duration_seconds), 0) / 60 FROM study_record", Long.class);
        result.put("totalAnswers", answers == null ? 0 : answers);
        result.put("wrongQuestions", wrongs == null ? 0 : wrongs);
        result.put("totalReviews", reviews == null ? 0 : reviews);
        result.put("totalStudyMinutes", studyMinutes == null ? 0 : studyMinutes);
        return Result.ok(result);
    }
}
