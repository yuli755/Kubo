package cn.hamm.spms.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>客户端 IP 解析（阶段 F-1：XFF 伪造防护）</h1>
 *
 * <p>原实现（{@code LoginBruteForceGuard}/{@code RateLimitHelper}）无条件信任
 * {@code X-Forwarded-For}/{@code X-Real-IP}：直连（或前置代理不清洗请求头）时，
 * 攻击者每次登录请求换一个伪造 XFF，IP 维度的防爆破锁定与公开端点限流即可被
 * 无限绕过——锁定/限流按伪造值计数，真实来源永远撞不到阈值。</p>
 *
 * <h2>规则</h2>
 * <ul>
 *   <li>仅当<b>直连对端</b>（{@code getRemoteAddr()}）命中
 *       {@code kubo.security.trusted-proxies} 白名单（CIDR 或精确 IP，默认空）时，
 *       才采信 {@code X-Forwarded-For}（取首个合法值）与 {@code X-Real-IP}；</li>
 *   <li>其余情况一律返回 {@code getRemoteAddr()}——伪造头不产生任何影响；</li>
 *   <li>默认空白名单 = 永不采信转发头，与直连部署形态一致；nginx 反代部署必须把
 *       代理自身地址/网段配置进白名单，否则所有用户按代理 IP 归并计数。</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    /** 精确 IP 白名单项 */
    private final List<String> trustedExact = new ArrayList<>();
    /** CIDR 白名单项 */
    private final List<Cidr> trustedCidrs = new ArrayList<>();

    /**
     * @param trustedProxiesRaw {@code kubo.security.trusted-proxies}：逗号分隔的
     *                          CIDR（如 10.0.0.0/8）或精确 IP（如 127.0.0.1），可为空
     */
    public ClientIpResolver(@Value("${kubo.security.trusted-proxies:}") String trustedProxiesRaw) {
        for (String item : StringUtils.commaDelimitedListToStringArray(trustedProxiesRaw)) {
            String entry = item.trim();
            if (entry.isEmpty()) {
                continue;
            }
            if (entry.contains("/")) {
                try {
                    trustedCidrs.add(Cidr.parse(entry));
                }
                catch (IllegalArgumentException e) {
                    log.warn("kubo.security.trusted-proxies 中非法 CIDR 已忽略: {}", entry);
                }
            }
            else {
                trustedExact.add(entry);
            }
        }
        if (trustedExact.isEmpty() && trustedCidrs.isEmpty()) {
            log.info("未配置 kubo.security.trusted-proxies，X-Forwarded-For/X-Real-IP 不参与客户端 IP 判定（直连形态）");
        }
    }

    /**
     * 解析客户端 IP：对端可信才采信转发头，否则取直连地址。
     *
     * @param request HTTP 请求
     * @return 客户端 IP，取不到返回空串
     */
    @NotNull
    public String resolve(@Nullable HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String remoteAddr = request.getRemoteAddr() == null ? "" : request.getRemoteAddr().trim();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            // XFF 是「客户端, 代理1, 代理2…」链路，最左端为声称的原始客户端；
            // 已确认对端可信（由其负责清洗/追加），取首个合法条目即可。
            for (String candidate : forwarded.split(",")) {
                String ip = candidate.trim();
                if (!ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                    return ip;
                }
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return remoteAddr;
    }

    /**
     * 直连对端地址是否命中可信代理白名单。
     *
     * @param ip 直连对端地址
     * @return 是否可信
     */
    public boolean isTrustedProxy(@Nullable String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        if (trustedExact.contains(ip)) {
            return true;
        }
        // CIDR 仅支持 IPv4；IPv6 对端只能靠精确项命中
        for (Cidr cidr : trustedCidrs) {
            if (cidr.contains(ip)) {
                return true;
            }
        }
        return false;
    }

    /**
     * IPv4 CIDR 网段（自实现，避免为一个匹配引入 commons-net 依赖）。
     *
     * @param network 网络地址（已按掩码对齐）
     * @param mask    32 位前缀掩码（如 /8 对应 0xFF000000）
     */
    private record Cidr(long network, long mask) {

        /**
         * 解析 a.b.c.d/len 形式的 CIDR。
         *
         * @param cidr CIDR 字符串
         * @return 解析结果
         * @throws IllegalArgumentException 格式非法
         */
        static @NotNull Cidr parse(@NotNull String cidr) {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("CIDR 须为 a.b.c.d/len 形式: " + cidr);
            }
            long prefix = Long.parseLong(parts[1].trim());
            if (prefix < 0 || prefix > 32) {
                throw new IllegalArgumentException("前缀长度须在 0-32 之间: " + cidr);
            }
            long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
            long host = toIpv4Long(parts[0].trim());
            return new Cidr(host & mask, mask);
        }

        /**
         * 判断地址是否落在网段内（含网络/广播地址，等价 inclusiveHostCount）。
         *
         * @param ip 待判定地址
         * @return 是否命中
         */
        boolean contains(@NotNull String ip) {
            try {
                return (toIpv4Long(ip) & mask) == network;
            }
            catch (IllegalArgumentException ignored) {
                // 非 IPv4 地址不参与网段匹配
                return false;
            }
        }

        private static long toIpv4Long(@NotNull String ip) {
            String[] octets = ip.split("\\.", -1);
            if (octets.length != 4) {
                throw new IllegalArgumentException("非 IPv4 地址: " + ip);
            }
            long value = 0;
            for (String octet : octets) {
                int part = Integer.parseInt(octet);
                if (part < 0 || part > 255) {
                    throw new IllegalArgumentException("IPv4 段越界: " + ip);
                }
                value = (value << 8) | part;
            }
            return value;
        }
    }
}
