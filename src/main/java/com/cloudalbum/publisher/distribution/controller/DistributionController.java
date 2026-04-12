package com.cloudalbum.publisher.distribution.controller;

import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import com.cloudalbum.publisher.common.model.Result;
import com.cloudalbum.publisher.common.util.SecurityUtil;
import com.cloudalbum.publisher.distribution.dto.DistributionCreateRequest;
import com.cloudalbum.publisher.distribution.dto.DistributionResponse;
import com.cloudalbum.publisher.distribution.dto.DistributionUpdateRequest;
import com.cloudalbum.publisher.distribution.service.DistributionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "内容分发管理")
@RestController
@RequestMapping("/api/v1/distributions")
@RequiredArgsConstructor
public class DistributionController {

    private final DistributionService distributionService;

    @Operation(summary = "获取所有分发规则（分页）")
    @GetMapping
    public Result<PageResult<DistributionResponse>> listDistributions(@Valid PageRequest pageRequest,
                                                                      @RequestParam(required = false) String status) {
        return Result.success(
                distributionService.listDistributions(SecurityUtil.getCurrentUserId(), pageRequest, status));
    }

    @Operation(summary = "获取当前生效的分发规则")
    @GetMapping("/active")
    public Result<List<DistributionResponse>> listActiveDistributions() {
        return Result.success(
                distributionService.listActiveDistributions(SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "获取分发规则详情")
    @GetMapping("/{id}")
    public Result<DistributionResponse> getDistribution(@PathVariable Long id) {
        return Result.success(
                distributionService.getDistribution(id, SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "创建分发规则")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<DistributionResponse> createDistribution(
            @Valid @RequestBody DistributionCreateRequest request) {
        return Result.success(
                distributionService.createDistribution(SecurityUtil.getCurrentUserId(), request));
    }

    @Operation(summary = "更新分发规则")
    @PutMapping("/{id}")
    public Result<DistributionResponse> updateDistribution(
            @PathVariable Long id,
            @Valid @RequestBody DistributionUpdateRequest request) {
        return Result.success(
                distributionService.updateDistribution(id, SecurityUtil.getCurrentUserId(), request));
    }

    @Operation(summary = "删除分发规则")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Result<Void> deleteDistribution(@PathVariable Long id) {
        distributionService.deleteDistribution(id, SecurityUtil.getCurrentUserId());
        return Result.success();
    }

    @Operation(summary = "生效分发规则（DRAFT/DISABLED -> ACTIVE）")
    @PatchMapping("/{id}/activate")
    public Result<DistributionResponse> activateDistribution(@PathVariable Long id) {
        return Result.success(
                distributionService.activateDistribution(id, SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "停用分发规则（ACTIVE -> DISABLED）")
    @PatchMapping("/{id}/disable")
    public Result<DistributionResponse> disableDistribution(@PathVariable Long id) {
        return Result.success(
                distributionService.disableDistribution(id, SecurityUtil.getCurrentUserId()));
    }
}
