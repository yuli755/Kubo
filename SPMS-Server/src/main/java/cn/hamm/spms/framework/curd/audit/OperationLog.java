package cn.hamm.spms.framework.curd.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <h1>通用操作审计标记</h1>
 *
 * <p>标在实体类上，表示该实体的<em>增 / 改 / 删</em>落库动作要被 {@code OperationLogRecorder} 记入
 * {@code operation_log} 表（谁在何时把哪条记录改成了什么）。这是 P3「通用操作审计日志」的<em>选择加入</em>开关：
 * 不标 = 不记，避免给全仓 40+ 资源无差别刷审计。</p>
 *
 * <p><b>与数据权限 deny 日志的边界</b>：deny 日志记「被拒了」，本标记记「改成了」——前者是数据权限的
 * 拒绝证据，后者是普通业务写操作的轨迹证据，两者互补、不是一回事。</p>
 *
 * <p>资源标识不在此注解里声明，而是沿 {@code DataPermissionResourceResolver} 的约定从 Service 类推导
 * （{@code MaterialService → asset.material}），保证与 {@code data_resource.resource_key} 一字不差。</p>

 * <p><b>2026-09-18 分层搬迁</b>：本类型原在 {@code cn.hamm.spms.module.system.datapermission.annotation}，
 * 由<b>框架层</b>搬来 —— 它是纯共享内核（只依赖 JDK），却被 {@code base/BaseService}、
 * {@code base/TenantAwareEntity} 这些下层类使用，构成"下层依赖业务域"的反向依赖。
 * 权限的<b>实现</b>（策略存储、拦截器、审计落库、规则编译）仍留在
 * {@code module.system.datapermission}；这里只留<b>抽象与上下文</b>。</p>
 *
 * @author Hamm.cn
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {
}
