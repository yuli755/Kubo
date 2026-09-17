package cn.hamm.spms.base.spi;

import cn.hamm.spms.base.BaseEntity;

/**
 * <h1>底座向业务域要的能力：自动编码取号</h1>
 *
 * <p>{@code base/BaseService#beforeSaveToDatabase} 是所有落库路径的必经点，自动编码必须在那一处补齐；
 * 而"编码规则长什么样、下一个号是多少"属于 {@code module.system.coderule}。于是这里只留一句抽象：
 * <b>把这个实体的自动编码字段填上</b>，取号与并发控制由实现负责。</p>
 *
 * <p><b>为什么接口在本包（base.spi）而不是 framework</b>：接口应当住在"使用它的最下层"。
 * base 是唯一使用者，把它放进 framework 会让 framework 认识一个它自己永远不用的能力，
 * 而放进 module 就又变成"下层依赖上层"。实现类在业务域里 {@code implements} 本接口 ——
 * 依赖方向是 module → base，正好是允许的那一档。</p>
 *
 * @author Hamm.cn
 */
public interface CodeRuleAllocator {
    /**
     * 填充实体上标记了自动编码的字段（已有值不动）
     *
     * @param entity 待落库实体
     * @param <E>    实体类型
     */
    <E extends BaseEntity<E>> void fillFieldAutoCode(E entity);
}
