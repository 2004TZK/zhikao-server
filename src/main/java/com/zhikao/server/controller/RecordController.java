package com.zhikao.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.server.common.Result;
import com.zhikao.server.entity.StudyRecord;
import com.zhikao.server.mapper.StudyRecordMapper;
import com.zhikao.server.security.AuthContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 学习记录模块（契约：6.9 GET /record/list）。
 */
@Tag(name = "学习记录", description = "学习记录时间线")
@RestController
@RequestMapping("/api/v1/record")
@RequiredArgsConstructor
public class RecordController {

    private final StudyRecordMapper studyRecordMapper;

    @Operation(summary = "学习记录时间线（支持按日期过滤）")
    @GetMapping("/list")
    public Result<Page<StudyRecord>> list(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String date) {
        LambdaQueryWrapper<StudyRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudyRecord::getUserId, AuthContext.getUserId());
        if (date != null && !date.isBlank()) {
            LocalDate day = LocalDate.parse(date);
            wrapper.ge(StudyRecord::getCreatedAt, day.atStartOfDay())
                    .lt(StudyRecord::getCreatedAt, day.plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(StudyRecord::getCreatedAt);
        return Result.ok(studyRecordMapper.selectPage(new Page<>(page, size), wrapper));
    }
}
