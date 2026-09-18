package cn.hamm.spms.module.system.tenant;

import cn.hamm.spms.base.BaseService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * <h1>租户服务</h1>
 *
 * <p>2026-09-18 建：分层评审发现 {@code UserController} 直接注入了 {@code TenantRepository} ——
 * 控制器是 HTTP 出入口，不该认识数据访问层（`pnpm layer:check` 的 L5 规则）。
 * 这里按本仓"每张业务表一个 Service"的既有约定补上租户服务，把那次回表收进来。</p>
 *
 * <p>两个方法都只是把原来的仓储调用收口，语义一字未改（{@code findAll} 与 {@code findAllById}
 * 的行为、返回集合类型都一样）；调用方从"控制器 → 仓储"变成"控制器 → 服务 → 仓储"。</p>
 *
 * @author Hamm.cn
 */
@Service
public class TenantService extends BaseService<TenantEntity, TenantRepository> {
    /**
     * 全部租户（根账号切换租户时用）
     *
     * @return 租户列表
     */
    public List<TenantEntity> listAll() {
        return repository.selectList(null);
    }

    /**
     * 按 ID 批量取租户
     *
     * @param ids 租户 ID 集合（空集合返回空列表，不会退化成全表查询）
     * @return 租户列表
     */
    public List<TenantEntity> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        // MP 的批量按主键查询叫 selectBatchIds（Spring Data 的 findAllById）
        return repository.selectBatchIds(ids);
    }
}
