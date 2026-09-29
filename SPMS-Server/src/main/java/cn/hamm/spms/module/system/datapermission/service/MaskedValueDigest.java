package cn.hamm.spms.module.system.datapermission.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * <h1>不可逆值摘要（脱敏写入口径的唯一实现）</h1>
 *
 * <p>把敏感值压成 {@code h:<sha256 前 12 位十六进制>}。两个调用方共用这一份实现，
 * 避免"同一个字段在接口响应里是 h:ab12cd34ef56、在审计日志里是另一种写法"的口径漂移：</p>
 * <ul>
 *   <li>{@link ColumnMaskingService}：{@code MaskType.HASH} 的响应侧脱敏。</li>
 *   <li>{@link OperationLogSnapshot}：审计快照里"永不明文"字段的落库形态。</li>
 * </ul>
 *
 * <p><b>审计为什么不能用 {@code ColumnMaskingService.maskFieldValue}</b>：那条路径的口径取决于
 * <em>当前请求者</em>的角色与策略，等于让"谁改的"决定库里存不存明文 —— 管理员改一次价格，
 * 明文就永久留在 {@code operation_log} 里，而这张表是原样返回给前端日志页的。
 * 摘要与观看者无关，因此可以在异步线程里安全计算。</p>
 *
 * <p><b>为什么不用常量占位符</b>：{@code before} 与 {@code after} 会变成同一个值，
 * 前端字段变更 Diff 就把"改了价格"显示成"没有改动" —— 那是假阴性，比不给信息更坏。
 * 值派生的摘要保留了"变没变"这个信息，只是不还原原值。</p>
 *
 * @author Hamm.cn
 */
public final class MaskedValueDigest {

    /** 摘要十六进制长度：够区分"变没变"，又不至于让日志页出现一长串噪声 */
    private static final int HEX_LENGTH = 12;

    /** 前缀。前端 {@code packages/shared/src/utils/mask.ts} 与 {@code MaskedWriteGuard} 都认这个形状 */
    private static final String PREFIX = "h:";

    /** 算法不可用时的兜底：宁可丢掉可比性，也不能把原值写进日志 */
    private static final String FALLBACK = "****";

    private MaskedValueDigest() {
    }

    /**
     * 计算字符串值的摘要。
     *
     * @param value 原始值，可为 null
     * @return {@code h:<12 位十六进制>}；null 返回 null（调用方按"无值"处理）；算法缺失返回 {@code ****}
     */
    public static String of(String value) {
        if (value == null) {
            return null;
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return PREFIX + hexString.substring(0, Math.min(HEX_LENGTH, hexString.length()));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法；走到这里说明运行环境异常，安全侧兜底
            return FALLBACK;
        }
    }

    /**
     * 计算任意值的摘要（先按字符串形态取值，与快照投影口径一致）。
     *
     * @param value 原始值，可为 null
     * @return 同 {@link #of(String)}
     */
    public static String ofValue(Object value) {
        return value == null ? null : of(String.valueOf(value));
    }
}
