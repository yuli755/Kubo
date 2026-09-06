package cn.hamm.spms.module.system.datapermission.service;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;

/**
 * <h1>脱敏占位值的写侧防护</h1>
 *
 * <p>列级脱敏一旦开启，<b>读侧输出的是字符串</b>（{@code SensitiveFieldSerializer} 把
 * {@code 13800138000} 写成 {@code "138****000"}）。而本项目的编辑表单是把详情接口的实体
 * 整体回填、再整体提交的，于是必然出现这条链路：</p>
 *
 * <pre>{@code
 *   getDetail → phone = "138****000"（掩码）
 *   → 用户只改昵称 → 提交整个实体
 *   → beforeUpdate 收到 "138****000"
 *   → 没有本类：掩码被当真实值写库，手机号从此损坏（且不可逆）
 *   → 有本类：直接拒绝，提示"该字段受限，请申请明文后再修改"
 * }</pre>
 *
 * <p>本类<b>只负责判定</b>，处置在 {@link SensitiveWriteProtection}。分开是为了让读侧
 * （{@code SensitiveFieldSerializer} 写出的掩码格式）与写侧共用同一个口径：
 * 两边各写一份"什么算掩码"，迟早漂移成"读侧遮了、写侧认不出"。</p>
 * <ul>
 *   <li>新增：没有"原值"可恢复，收到掩码就是客户端异常，直接拒绝；</li>
 *   <li>修改：用户其实只想改昵称，把整条保存请求打回去是最差体验；
 *       此时应把该字段恢复成库里的值 —— 语义上"这个字段他没动"，
 *       既不会把 {@code 138****000} 写进库，也不会因为一个受限字段让他改不了其它字段。
 *       <b>不要</b>让前端"提交时删掉该字段"：删字段等于把 {@code phone=null} 传下去，
 *       在"整实体更新"的框架语义里就是一次清空。</li>
 * </ul>
 *
 * <p><b>数值型敏感字段（如 {@code Double price}）不能靠"置 null + 恢复"处理</b>：
 * {@code PurchasePriceEntity#price} 上挂着 {@code @NotNull(groups = WhenUpdate)}，
 * 置 null 会被参数校验打回成"采购单价不能为空"这个与权限无关的假错误。
 * 因此 {@code SensitiveFieldDeserializer} 对非字符串目标类型直接报"该字段受限"，
 * 宁可让受限角色改不了这条记录，也不用 sentinel（写 0 / 空串）兜底 ——
 * 一旦恢复链路某处没接上，兜底值就静默入住了。</p>
 *
 * <p>判定口径与前端 {@code looksMasked()} 一致：连续两个 {@code *}、整串星号、
 * 或 {@code h:} + 十六进制摘要。单个星号不算（{@code a*b} 这类真实文本不能误伤）。</p>
 *
 * @author Hamm.cn
 */
public final class MaskedWriteGuard {

    private MaskedWriteGuard() {
    }

    /**
     * 是否看起来是脱敏占位值（而非用户真实输入）
     *
     * @param value 提交值
     * @return true 表示这是掩码，不能落库
     */
    public static boolean looksLikeMaskedPlaceholder(Object value) {
        if (value == null) {
            return false;
        }
        String text = value.toString();
        if (text.isEmpty()) {
            return false;
        }
        return text.contains("**")
                || text.matches("\*+")
                || text.matches("(?i)^h:[0-9a-f]{6,}$");
    }

}
