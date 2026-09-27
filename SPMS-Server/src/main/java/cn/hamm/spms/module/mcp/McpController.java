package cn.hamm.spms.module.mcp;

import cn.hamm.airpower.ai.mcp.McpService;
import cn.hamm.airpower.ai.mcp.model.McpRequest;
import cn.hamm.airpower.ai.mcp.model.McpResponse;
import cn.hamm.airpower.api.ApiController;
import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.core.AccessTokenUtil;
import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.curd.permission.Permission;
import cn.hamm.spms.common.interceptor.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import static cn.hamm.airpower.exception.Errors.PARAM_MISSING;

/**
 * <h1>MCP</h1>
 *
 * @author Hamm.cn
 */
@Api("mcp")
@Slf4j
public class McpController extends ApiController {
    @Autowired
    private McpService mcpService;

    @Autowired
    private RequestInterceptor requestInterceptor;

    @PostMapping("")
    @Permission(login = false)
    public McpResponse messages(HttpServletRequest request, @RequestBody McpRequest mcpRequest) {
        try {
            // 优先 Authorization 头（RFC 6750 推荐；URL 查询串会进访问日志/历史），
            // ?token= 保留为兼容通道：存量 MCP 客户端配置里的连接串不带请求头
            String accessToken = request.getHeader("Authorization");
            if (accessToken != null && accessToken.startsWith("Bearer ")) {
                accessToken = accessToken.substring(7);
            }
            if (!StringUtils.hasText(accessToken)) {
                accessToken = request.getParameter("token");
            }
            PARAM_MISSING.whenNull(accessToken, "accessToken is required");
            AccessTokenUtil.VerifiedToken verifiedToken = requestInterceptor.getVerifiedToken(accessToken);
            return mcpService.run(mcpRequest, (mcpTool -> requestInterceptor.checkUserPermission(
                    verifiedToken, McpService.getPermissionIdentity(mcpTool), request
            )));
        } catch (ServiceException e) {
            McpResponse response = new McpResponse().setError(new McpResponse.McpError(e));
            response.setId(mcpRequest.getId());
            return response;
        }
    }

    @PostMapping("getMcpTools")
    @Description("获取 MCP 工具列表")
    @Permission(authorize = false)
    public Json messages() {
        return Json.data(McpService.tools);
    }
}
