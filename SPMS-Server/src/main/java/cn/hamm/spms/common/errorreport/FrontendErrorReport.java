package cn.hamm.spms.common.errorreport;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <h1>前端错误上报请求体</h1>
 *
 * <p>与前端 {@code src/utils/errorHandler.ts} 的 {@code ErrorReport} 字段一一对应：</p>
 * <ul>
 *   <li>{@code message}：错误消息（必填，长度受控）；</li>
 *   <li>{@code stack}：错误堆栈（可选，可能含 URL/token，后端脱敏截断）；</li>
 *   <li>{@code source}：错误来源（{@code vue}/{@code promise}/{@code window}/{@code manual}）；</li>
 *   <li>{@code context}：业务上下文（如 login/websocket，帮助定位）；</li>
 *   <li>{@code time}：客户端上报时间戳（毫秒）。</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class FrontendErrorReport {

    private String message;

    private String stack;

    private String source;

    private String context;

    private Long time;
}
