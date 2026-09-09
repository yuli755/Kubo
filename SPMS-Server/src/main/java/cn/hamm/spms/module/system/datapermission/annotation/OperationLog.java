package cn.hamm.spms.module.system.datapermission.annotation;

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
 *
 * @author Hamm.cn
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {
}
