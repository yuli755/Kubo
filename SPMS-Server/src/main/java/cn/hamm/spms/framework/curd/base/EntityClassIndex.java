package cn.hamm.spms.framework.curd.base;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * <h1>实体类索引（可注入的那一份）</h1>
 *
 * <p>{@link EntityMetadata} 是 J3 之后"哪个类是实体、它对应哪张表、哪些字段是列"的唯一实现；
 * 但它是纯静态工具，而调用它的服务需要能在单测里替换掉"有哪些实体"这个输入
 * （单测的实体是测试内部类，类路径扫描的结果与生产不同）。所以这里把同一份实现包成 Bean：
 * <b>实现只有一份，形状有两种</b> —— 静态调用给盘点脚本式代码用，注入式给可测的服务用。</p>
 *
 * @author Arena agent
 */
@Component
public class EntityClassIndex {

    /**
     * 全部实体类（扫 {@code @TableName}）
     *
     * @return 实体类集合
     */
    public Collection<Class<?>> classes() {
        return EntityMetadata.entityClasses();
    }

    /**
     * 表名（归一化）→ 实体类
     *
     * @return 索引
     */
    public Map<String, Class<?>> byTable() {
        return EntityMetadata.byTable(classes());
    }

    /**
     * 按物理表名找实体类
     *
     * @param rawTableName 物理表名（大小写/反引号都容忍）
     * @return 实体类；没有对应实体返回 {@code null}
     */
    public Class<?> byTableName(String rawTableName) {
        if (rawTableName == null) {
            return null;
        }
        String normalized = rawTableName.trim().toLowerCase(Locale.ROOT).replace("`", "");
        return byTable().get(normalized);
    }
}
