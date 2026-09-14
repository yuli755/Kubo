package cn.hamm.spms.common.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * <h1>安全响应头过滤器（S4 · 第一批：零风险头）</h1>
 *
 * <p>给所有 HTTP 响应注入「确定不会破坏前端」的安全头，收窄常见攻击面。刻意<b>不含 CSP</b>：
 * CSP 涉及前端内联脚本（index.html 防闪烁脚本）、ECharts 的 data URI 与 WebSocket 连接，
 * 配置错误会直接白屏，且本仓库前端由 Vite/Nginx 托管、后端 {@code add-mappings=false} 不托管
 * 静态资源，因此 CSP 应在前端托管层/真实环境回归后再收紧（见方案文档 S4 的「第二阶段」）。</p>
 *
 * <p>注入的头与理由：</p>
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} —— 禁 MIME 嗅探，防把 JSON 当 HTML 执行；</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin} —— 跨源不携带完整 URL，避免令牌/路径经 Referer 泄露；</li>
 *   <li>{@code X-XSS-Protection: 0} —— 关闭被浏览器弃用且自身可被绕过的旧 XSS 过滤器，转由 CSP 承担；</li>
 *   <li>{@code Permissions-Policy} —— 管理后台无需摄像头/麦克风/定位/支付，显式禁用。</li>
 * </ul>
 *
 * <p><b>注意</b>：{@code X-Frame-Options} 未在此注入 —— 后端只产出 JSON API 与 WebSocket，
 * 防点击劫持应加在前端 HTML 托管层（Nginx）；在 API 层加它既无意义，也非本过滤器职责。</p>
 *
 * @author Hamm.cn
 */
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse httpResponse) {
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
            httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            httpResponse.setHeader("X-XSS-Protection", "0");
            httpResponse.setHeader(
                    "Permissions-Policy",
                    "camera=(), microphone=(), geolocation=(), payment=()"
            );
        }
        chain.doFilter(request, response);
    }
}
