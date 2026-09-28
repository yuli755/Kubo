package cn.hamm.spms.common.security;

import cn.hamm.spms.framework.error.BusinessException;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * <h1>Webhook 出站地址守卫（SSRF）</h1>
 *
 * <p>通知（{@code notify}）的地址是运营可配的，等价于把「服务端向任意地址发请求」
 * 的能力交给了持有 notify 管理权限的人。本守卫在<b>保存时与发送时</b>各校验一次，
 * 把目标收敛到公网 http(s)：协议白名单 + 主机名解析后逐地址拒绝内网/环回/链路本地
 * （169.254 = 云 metadata）/组播/CGNAT(100.64/10)/IPv6 ULA(fc00::/7)。</p>
 *
 * <p>已知残余：DNS rebinding（校验后重解析换到内网）与响应重定向，需配合
 * {@code NotifyService} 的 NEVER 重定向发送端；彻底闭环要固定解析结果直连，
 * 留待网关层。</p>
 *
 * @author Hamm.cn
 */
public final class WebhookUrlGuard {

    /**
     * 邮箱形态（EMAIL 渠道的 url 字段存的是收件人）：单地址、禁空白与控制字符，
     * 顺带堵住 CRLF 邮件头注入
     */
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private WebhookUrlGuard() {
    }

    /**
     * 校验 http(s) webhook 地址；不合规直接抛业务异常（不返回脱敏提示以外的细节）
     *
     * @param rawUrl 原始地址
     */
    public static void checkHttpUrl(@NotNull String rawUrl) {
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (Exception e) {
            throw new BusinessException("通知地址不是合法的 URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new BusinessException("通知地址仅支持 http/https");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException("通知地址缺少主机名");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new BusinessException("通知地址无法解析");
        }
        if (addresses.length == 0) {
            throw new BusinessException("通知地址无法解析");
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                throw new BusinessException("通知地址不允许指向内网或保留地址");
            }
        }
    }

    /**
     * 校验邮件收件人（EMAIL 渠道）；单地址且禁 CRLF
     *
     * @param recipient 收件人
     */
    public static void checkEmailRecipient(@NotNull String recipient) {
        if (!EMAIL.matcher(recipient.trim()).matches()) {
            throw new BusinessException("通知邮箱地址不合法");
        }
    }

    /**
     * 内网/保留地址判定（含 Java 默认口径没盖住的 CGNAT 与 IPv6 ULA）
     */
    private static boolean isBlocked(@NotNull InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        // 100.64.0.0/10（运营商级 NAT，Java 不算 site-local，但内网可达）
        if (bytes.length == 4 && (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xFF) >= 64 && (bytes[1] & 0xFF) <= 127) {
            return true;
        }
        // fc00::/7（IPv6 唯一本地地址，Java 的 isSiteLocalAddress 只认过时的 fec0::/10）
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }
}
