package cn.hamm.spms.base;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * <h1>写入前的框架级填充（J3：{@code @PrePersist} 的接替者）</h1>
 *
 * <p>归属字段（{@code tenant_id}/{@code creator_id}）原来是 {@link TenantAwareEntity#fillAttribution()} 上的
 * JPA 生命周期回调 {@code @PrePersist} —— Hibernate 落库前自动调。J3 之后没有 Hibernate 生命周期了，
 * 而这件事<b>不能挪进 CurdService</b>：仓库里还有几条绕过引擎的写入（出站记录器、拒绝日志、影子对账行），
 * 它们同样必须拿到归属。MyBatis-Plus 为这种"所有 insert 都要经过的钩子"提供的正是
 * {@link MetaObjectHandler}，所以等价物放在这里。</p>
 *
 * <p>为什么类住在 {@code base} 而不是 {@code framework}：它要认识 {@link TenantAwareEntity}（业务基类），
 * 而 framework 不许反向依赖 base（分层门禁 L1）。</p>
 *
 * <p>只做归属填充，不做 create_time/update_time：那两个字段的写入时机在引擎里是显式业务语义
 * （"更新不允许改创建时间"），挪到这里会变成第二条路径。</p>
 *
 * @author Arena agent
 */
@Component
public class KuboMetaObjectHandler implements MetaObjectHandler {

    /**
     * 所有 MyBatis-Plus insert 都会走到这里：补齐归属（含绕过引擎的记录器）
     *
     * @param metaObject 待插入对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        if (metaObject.getOriginalObject() instanceof TenantAwareEntity<?> entity) {
            entity.fillAttribution();
        }
    }

    /**
     * 更新不做框架级填充
     *
     * @param metaObject 待更新对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // 归属的覆盖保护由 BaseService + AttributionWriteProtection 负责（要回表拿既存值，见那里的说明）
    }
}
