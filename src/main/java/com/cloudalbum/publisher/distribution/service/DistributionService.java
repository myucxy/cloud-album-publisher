package com.cloudalbum.publisher.distribution.service;

import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import com.cloudalbum.publisher.distribution.dto.DistributionCreateRequest;
import com.cloudalbum.publisher.distribution.dto.DistributionResponse;
import com.cloudalbum.publisher.distribution.dto.DistributionUpdateRequest;

import java.util.List;

public interface DistributionService {

    /** 创建分发规则 */
    DistributionResponse createDistribution(Long userId, DistributionCreateRequest request);

    /** 分页查询当前用户的分发规则 */
    PageResult<DistributionResponse> listDistributions(Long userId, PageRequest pageRequest, String status);

    /** 查询规则详情 */
    DistributionResponse getDistribution(Long id, Long userId);

    /** 更新规则（含设备列表全量替换） */
    DistributionResponse updateDistribution(Long id, Long userId, DistributionUpdateRequest request);

    /** 删除规则（逻辑删除） */
    void deleteDistribution(Long id, Long userId);

    /** 生效规则 DRAFT/DISABLED -> ACTIVE */
    DistributionResponse activateDistribution(Long id, Long userId);

    /** 停用规则 ACTIVE -> DISABLED */
    DistributionResponse disableDistribution(Long id, Long userId);

    /** 查询当前用户所有 ACTIVE 状态的规则 */
    List<DistributionResponse> listActiveDistributions(Long userId);
}
