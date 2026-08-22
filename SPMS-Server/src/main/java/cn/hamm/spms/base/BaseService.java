package cn.hamm.spms.base;

import cn.hamm.airpower.curd.base.CurdService;
import cn.hamm.spms.module.system.SystemServices;
import cn.hamm.spms.module.system.coderule.CodeRuleService;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionGuard;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionResourceResolver;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/**
 * <h1>基础服务类</h1>
 *
 * @param <E> 实体
 * @param <R> 数据源
 * @author Hamm.cn
 */
@Slf4j
public class BaseService<
        E extends BaseEntity<E>,
        R extends BaseRepository<E>
        > extends CurdService<E, R> {

    /**
     * 当前服务的数据库最后一次确认
     *
     * @param entity 实体
     * @return 处理后的数据
     */
    protected E beforeAppSaveToDatabase(@NotNull E entity) {
        return entity;
    }

    @Override
    protected final @NotNull E beforeSaveToDatabase(@NotNull E entity) {
        CodeRuleService codeRuleService = SystemServices.getCodeRuleService();
        codeRuleService.fillFieldAutoCode(entity);
        return beforeAppSaveToDatabase(entity);
    }

    @Override
    protected E beforeAdd(@NotNull E entity) {
        E result = super.beforeAdd(entity);
        enforceDataPermission(DataPermissionAction.CREATE);
        return result;
    }

    @Override
    protected E beforeUpdate(@NotNull E entity) {
        E result = super.beforeUpdate(entity);
        enforceDataPermission(DataPermissionAction.UPDATE);
        return result;
    }

    @Override
    protected void beforeDelete(@NotNull E entity) {
        super.beforeDelete(entity);
        enforceDataPermission(DataPermissionAction.DELETE);
    }

    /**
     * 数据权限写入守卫：校验当前主体是否拥有该资源对应写入动作的权限
     *
     * @param action 写入动作
     */
    protected void enforceDataPermission(@NotNull DataPermissionAction action) {
        DataPermissionGuard dataPermissionGuard = SystemServices.getDataPermissionGuard();
        if (dataPermissionGuard == null) {
            return;
        }
        String resourceKey = DataPermissionResourceResolver.resolve(getClass());
        if (resourceKey == null) {
            return;
        }
        dataPermissionGuard.enforce(resourceKey, action);
    }

    /**
     * 发布
     *
     * @param id ID
     */
    public final void publish(long id) {
        E entity = get(id);
        beforePublish(entity);
        updateToDatabase(getEntityInstance(id).setIsPublished(true));
    }

    /**
     * 发布前
     *
     * @param entity 实体
     */
    protected void beforePublish(@NotNull E entity) {
        log.info("单据发布前，ID:{}", entity.getId());
    }

    @Override
    protected E afterGet(@NotNull E entity) {
        return afterAppGet(entity);
    }

    protected E afterAppGet(@NotNull E entity) {
        return entity;
    }

    @Override
    protected final void afterAdd(long id, @NotNull E source) {
        afterAppAdd(id, source);
    }

    protected void afterAppAdd(long id, @NotNull E source) {
    }

    @Override
    protected void afterUpdate(long id, @NotNull E source) {
        afterAppUpdate(id, source);
    }

    protected void afterAppUpdate(long id, @NotNull E source) {
    }
}
