package cn.hamm.spms.module.system;

import cn.hamm.airpower.api.ApiController;
import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.core.annotation.Description;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * <h1>Controller</h1>
 *
 * <p>⚠️ 未实现：企业微信集成是占位空壳。当前只有 {@link #callback()} 返回一个占位字符串，
 * 没有企业微信回调验签、消息接收、成员同步、登录集成等任何实质逻辑。见
 * {@code docs/业务功能未开发盘点.md} §1.3。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Api("wecom")
@Description("企业微信")
public class WecomController extends ApiController {
    @GetMapping("callback")
    public String callback() {
        // 占位：企业微信回调未实现。保留本端点仅为占位，不承载任何业务。
        return "<h1>Server running! </h1>";
    }
}
