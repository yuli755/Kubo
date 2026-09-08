package cn.hamm.spms.module.system.datapermission.repository;

import cn.hamm.spms.base.BaseRepository;
import cn.hamm.spms.module.system.datapermission.entity.PermissionChangeOutboxEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * <h1>权限变更 Outbox 仓库</h1>
 *
 * @author Hamm.cn
 */
@Repository
public interface PermissionChangeOutboxRepository extends BaseRepository<PermissionChangeOutboxEntity> {
    PermissionChangeOutboxEntity getByEventKey(String eventKey);

    List<PermissionChangeOutboxEntity> findTop100ByStatusAndNextRetryAtLessThanEqualOrderByIdAsc(String status, Long nextRetryAt);

    List<PermissionChangeOutboxEntity> findByStatusOrderByIdDesc(String status);
}
