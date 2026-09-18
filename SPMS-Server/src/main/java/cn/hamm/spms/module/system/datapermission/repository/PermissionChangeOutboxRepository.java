package cn.hamm.spms.module.system.datapermission.repository;

import cn.hamm.spms.base.BaseRepository;
import cn.hamm.spms.module.system.datapermission.entity.PermissionChangeOutboxEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * <h1>权限变更 Outbox 仓库</h1>
 *
 * @author Hamm.cn
 */
@Repository
public interface PermissionChangeOutboxRepository extends BaseRepository<PermissionChangeOutboxEntity> {
    /**
     * 按事件键查询（幂等去重）
     *
     * @param eventKey 事件键
     * @return 事件
     */
    default PermissionChangeOutboxEntity getByEventKey(String eventKey) {
        return selectOne(new QueryWrapper<PermissionChangeOutboxEntity>().eq("event_key", eventKey));
    }

    /**
     * 取前 100 条待重试事件（按 ID 升序）
     *
     * @param status      状态
     * @param nextRetryAt 下次重试时间
     * @return 事件列表
     * @apiNote 派生名里的 {@code Top100} 是 limit 100：这里用 MP 的分页对象表达
     * （{@code searchCount = false}，只取一段，不额外发一条 count 语句），
     * 而不是 {@code last("LIMIT 100")} —— 后者是把 SQL 写回 Java。
     */
    default List<PermissionChangeOutboxEntity> findTop100ByStatusAndNextRetryAtLessThanEqualOrderByIdAsc(String status, Long nextRetryAt) {
        QueryWrapper<PermissionChangeOutboxEntity> wrapper = new QueryWrapper<PermissionChangeOutboxEntity>()
                .eq("status", status)
                .le("next_retry_at", nextRetryAt)
                .orderByAsc("id");
        return selectPage(new Page<>(1, 100, false), wrapper).getRecords();
    }

    /**
     * 按状态查询（按 ID 倒序）
     *
     * @param status 状态
     * @return 事件列表
     */
    default List<PermissionChangeOutboxEntity> findByStatusOrderByIdDesc(String status) {
        return selectList(new QueryWrapper<PermissionChangeOutboxEntity>()
                .eq("status", status)
                .orderByDesc("id"));
    }
}
