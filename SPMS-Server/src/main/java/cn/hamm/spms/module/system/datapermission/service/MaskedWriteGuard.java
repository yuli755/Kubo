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
 * <p>本类提供两种处置，<b>新增用拒绝、修改用还原</b>：</p>
 * <ul>
 *   <li>新增：没有"原值"可恢复，收到掩码就是客户端异常，直接拒绝；</li>
 *   <li>修改：用户其实只想改昵称，把整条保存请求打回去是最差体验；
 *       此时应把该字段恢复成库里的值 —— 语义上"这个字段他没动"，
 *       既不会把 {@code 138****000} 写进库，也不会因为一个受限字段让他改不了其它字段。
 *       <b>不要</b>让前端"提交时删掉该字段"：删字段等于把 {@code phone=null} 传下去，
 *       在"整实体更新"的框架语义里就是一次清空。</li>
 * </ul>
 *
 * <p>数值型敏感字段（如 {@code Double price}）更早就失败：{@code "****"} 反序列化成
 * {@code Double} 会抛 {@code InvalidFormatException}，接口直接 400，且报错信息里看不出
 * 跟权限有关。<b>这一类本类拦不住</b>（失败发生在进入 service 之前），
 * 所以前端必须在提交前剔除受限字段，见 {@code useMaskPolicy().omitRestricted}。</p>
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

    /**
     * 掩码值一律拒绝落库
     *
     * @param label 字段中文名（出现在给用户的报错里）
     * @param value 提交值
     */
    public static void rejectMaskedPlaceholder(String label, Object value) {
        if (looksLikeMaskedPlaceholder(value)) {
            PARAM_INVALID.show(label + "提交的是脱敏占位值，无法作为新值创建记录。"
                    + "该字段对当前角色受限，请向数据权限管理员申请明文后再操作");
        }
    }

    /**
     * 修改场景：提交值是掩码时保留库里的原值
     *
     * @param label     字段中文名（写日志用）
     * @param incoming  本次提交的值
     * @param stored    库里现值（可为 null，表示查不到）
     * @return 应当落库的值：提交值不是掩码时原样返回，是掩码时返回 {@code stored}
     */
    public static Object keepStoredWhenMasked(String label, Object incoming, Object stored) {
        if (!looksLikeMaskedPlaceholder(incoming)) {
            return incoming;
        }
        // 只在"确实是掩码"时兜底，并留一条可查的轨迹：否则运维会以为脱敏把数据改坏了
        org.slf4j.LoggerFactory.getLogger(MaskedWriteGuard.class)
                .info("字段[{}]提交值为脱敏占位，已保留库中原值（当前角色无明文权限）", label);
        return stored;
    }
}
