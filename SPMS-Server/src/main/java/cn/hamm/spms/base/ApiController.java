package cn.hamm.spms.base;

import cn.hamm.spms.common.security.AccessTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;

/**
 * <h1>API 控制器基础根类</h1>
 *
 * <p>为所有业务与基础控制器提供通用的请求上下文与当前登录用户解析能力。
 * 替代旧 {@code cn.hamm.airpower.api.ApiController}。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Controller
public class ApiController {
    @Autowired(required = false)
    protected HttpServletRequest request;

    @Value("${spms.api.access-token-secret:${airpower.api.access-token-secret:1234567890}}")
    protected String accessTokenSecret;

    @Value("${spms.api.authorize-header:${airpower.api.authorize-header:Authorization}}")
    protected String authorizeHeader;

    /**
     * 获取当前登录用户的 ID
     *
     * @return 用户 ID
     */
    protected long getCurrentUserId() {
        if (request == null) {
            return 0L;
        }
        String accessToken = request.getHeader(authorizeHeader);
        if (!StringUtils.hasText(accessToken)) {
            accessToken = request.getParameter(authorizeHeader);
        }
        if (!StringUtils.hasText(accessToken)) {
            return 0L;
        }
        if (accessToken.startsWith("Bearer ") || accessToken.startsWith("bearer ")) {
            accessToken = accessToken.substring(7).trim();
        }
        try {
            AccessTokenUtil.VerifiedToken verifiedToken = AccessTokenUtil.create().verify(
                    accessToken,
                    accessTokenSecret
            );
            return verifiedToken.getPayloadId();
        } catch (Exception e) {
            log.debug("解析当前用户 ID 失败: {}", e.getMessage());
            return 0L;
        }
    }
}
