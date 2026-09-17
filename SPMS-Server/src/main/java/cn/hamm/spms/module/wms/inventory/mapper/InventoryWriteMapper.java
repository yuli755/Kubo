package cn.hamm.spms.module.wms.inventory.mapper;

import cn.hamm.spms.base.mapper.KuboInfraMapper;
import cn.hamm.spms.module.wms.inventory.InventoryEntity;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

/**
 * <h1>库存写侧 Mapper（原子增减）</h1>
 *
 * <p>两条语句都不做"先读后写"，而是把算术交给数据库：{@code quantity = quantity + delta}。
 * 任意交错执行下总量恒等 —— 这是阶段 F-3 修掉"交叉移库丢数量"的那个修复，
 * 换 ORM 时它必须原样保留（{@code pnpm jpa:check} 的注释里记着这段来历）。</p>
 *
 * <p><b>为什么单独一个接口，而不是塞进 {@code InventoryMapper}（读侧）</b>：
 * 读侧 Mapper 由 {@code kubo.data-permission.mapper-read.enabled} 一刀控制，默认关。
 * 关掉读写侧是"读端点整体消失"，但如果写侧的增减也跟着消失，移库/入库就会静默不扣库存 ——
 * 所以这个接口属于 {@link KuboInfraMapper} 族（无条件注册）。</p>
 *
 * <p><b>SQL 在哪</b>：{@code src/main/resources/mapper/wms/InventoryWriteMapper.xml}。
 * Java 里只有声明（用户 2026-09-18 的分层规则：禁止 {@code .java} 写 SQL）。</p>
 *
 * @author Hamm.cn
 */
@Repository
public interface InventoryWriteMapper extends KuboInfraMapper<InventoryEntity> {
    /**
     * 原子增加库存数量
     *
     * @param id    库存行 ID
     * @param delta 增量
     * @param now   更新时间戳（毫秒）
     * @return 受影响行数（0 表示行不存在）
     */
    int increaseQuantityById(@Param("id") long id, @Param("delta") double delta, @Param("now") long now);

    /**
     * 原子扣减库存数量（"扣成负数"时不更新，守卫在 WHERE 里）
     *
     * @param id    库存行 ID
     * @param minus 扣减量
     * @param now   更新时间戳（毫秒）
     * @return 受影响行数（0 表示行不存在或数量不足）
     */
    int decreaseQuantityById(@Param("id") long id, @Param("minus") double minus, @Param("now") long now);
}
