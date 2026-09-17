package cn.hamm.spms.base.spi;

import cn.hamm.spms.framework.curd.datapermission.PermissionContext;

import java.util.function.Supplier;

/**
 * <h1>底座向业务域要的能力：受限字段的写侧处置</h1>
 *
 * <p>受限字段（{@code @SensitiveField}）在读侧会被换成掩码串，前端把它原样提交回来时，
 * 库里就会留下 {@code 138****000} 这种<b>不可逆的假值</b>。处置规则很窄，也刻意不做任何"猜"：</p>
 * <ul>
 *   <li><b>新增</b>：没有原值可恢复 → 明确拒绝（见 {@link #rejectMaskedOnAdd}）；</li>
 *   <li><b>更新</b>：本次请求确实收到过掩码占位，或提交值等于我们自己发出去的那串 →
 *       用库中既存值覆盖回去（见 {@link #restoreOnUpdate}）；</li>
 *   <li><b>用户主动清空</b>（提交 {@code null}）不算掩码，照写不误 ——
 *       这正是必须"登记"而不能只看实体的原因。</li>
 * </ul>
 *
 * <p><b>为什么接口方法长这样</b>：判定"要不要回表"与"回表取到的行长什么样"分属两层 ——
 * 前者是策略与登记（业务域），后者是仓储访问（base 才有 repository）。所以这里让 base
 * 把取数动作作为 {@link Supplier} 交进来：<b>由实现决定要不要真的取数</b>，
 * 不命中时一次库都不查，正常保存仍是一次 UPDATE。</p>
 *
 * @author Hamm.cn
 */
public interface SensitiveFieldProtector {
    /**
     * 新增时的拒绝：提交值是掩码占位时明确报错
     *
     * <p>新增没有"原值"可恢复，放行的结果就是把掩码建进库。宁可让这次保存失败并说清楚原因，
     * 也不能静默写入一个假值。未命中任何受限字段时零开销。</p>
     *
     * @param incoming 待写入实体
     */
    void rejectMaskedOnAdd(Object incoming);

    /**
     * 更新时的受限字段恢复：需要时用库中既存行覆盖提交值里的掩码占位
     *
     * <p>两条互补的识别路径，都不做"猜"：</p>
     * <ol>
     *   <li><b>登记命中</b>：本次请求确实收到过掩码占位（反序列化阶段登记，零成本）；
     *       这一条是<b>严格</b>的 —— 取不到既存行时必须让保存失败，
     *       因为那时放行等于把 {@code null}（或掩码串）写进真实列；</li>
     *   <li><b>输出复算</b>：拿库中值套用同一份生效策略，算出"这个用户本该看到的串"再比对，
     *       覆盖 {@code maskExpression} 配成非星号文本（如"已隐藏"）的情况。这条不严格：
     *       取不到既存行就直接放行（行都没了，恢复无从谈起）。</li>
     * </ol>
     *
     * <p>两条都不命中时<b>不调用</b> {@code storedLoader}，也不改动入参。</p>
     *
     * @param incoming     本次提交的实体（就地修改，只改被判定为掩码的字段）
     * @param context      当前权限上下文（可为 {@code null}：非 HTTP 入口没有上下文）
     * @param storedLoader 取库中既存行；返回 {@code null} 表示行不存在或无权访问
     * @return 是否真的做了恢复
     */
    boolean restoreOnUpdate(Object incoming, PermissionContext context, Supplier<Object> storedLoader);
}
