package cn.hamm.spms.framework.curd.datapermission;

import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import org.springframework.stereotype.Component;


/**
 * <h1>数据权限资源标识解析</h1>
 *
 * <p>将业务实体/服务映射为数据权限资源标识（resourceKey）。约定是
 * {@code module.entityName}，其中尾段取实体类名并<b>只降首字母</b>：
 * {@code MaterialService} → {@code asset.material}、
 * {@code PurchasePriceService} → {@code channel.purchasePrice}。</p>
 *
 * <p><b>为什么强调大小写</b>：{@code data_resource.resource_key} 里存在 camelCase 尾段
 * （{@code channel.purchasePrice} / {@code channel.salePrice}），曾经这里整串
 * {@code toLowerCase()}，于是这两个模块的策略永远查不到资源 —— 保存照样成功、
 * 行权限照样"已配置"，只是防护为零。资源键是<b>跨表外键式的字符串</b>，
 * 比较必须与注册表一字不差，不能靠"顺便规范化一下"。</p>

 * <p><b>2026-09-18 分层搬迁（为什么在框架层）</b>：本类原在 {@code cn.hamm.spms.module.system.datapermission.service}，它只依赖 JDK / 框架自身，却被 base 层与业务域同时使用 —— 留在业务域里就构成"下层依赖上层"，于是搬进框架层。
 * 搬迁前它是 base/BaseService 白名单里的"下层依赖业务域"条目之一，搬完白名单条目数 15 → 12。</p>
 *
 * @author Hamm.cn
 */
@Component
public class DataPermissionResourceResolver {
    private DataPermissionResourceResolver() {
    }

    /**
     * 从业务 Service 解析数据权限资源标识
     *
     * @param serviceClass 业务 Service 类（如 {@code InputService}）
     * @return 资源标识（如 {@code wms.input}）；无法解析时返回 {@code null}
     */
    public static String resolve(Class<?> serviceClass) {
        if (serviceClass == null) {
            return null;
        }
        String name = serviceClass.getSimpleName();
        if (!name.endsWith("Service")) {
            return null;
        }
        String entityName = name.substring(0, name.length() - "Service".length());

        String module = resolveModuleName(serviceClass);
        if (module == null) {
            return null;
        }
        // 只降首字母，不整串小写：注册键的尾段是 camelCase（data_resource 里就有
        // channel.purchasePrice / channel.salePrice / dataPolicy 这类），整串小写会得到
        // channel.purchaseprice -> 这些模块的行级策略永远查不到资源：保存成功、防护为零、测试还全绿。
        return module + "." + decapitalize(entityName);
    }

    /**
     * 首字母小写（JavaBean 属性名口径），其余字符原样保留
     */
    static String decapitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        // 前两位都是大写时视为缩写（URL / ID / BOM），保持原样，避免把 BOMService 变成 bOMService
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1)) && Character.isUpperCase(name.charAt(0))) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * 从 Service 的包路径解析所属业务模块
     */
    private static String resolveModuleName(Class<?> serviceClass) {
        String packageName = serviceClass.getPackageName();
        int idx = packageName.indexOf("module.");
        if (idx < 0) {
            return null;
        }
        String rest = packageName.substring(idx + "module.".length());
        int end = rest.indexOf('.');
        return end < 0 ? rest : rest.substring(0, end);
    }
}