package cn.hamm.spms.module.wechat;

import cn.hamm.airpower.api.ApiController;
import cn.hamm.airpower.curd.permission.Permission;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * <h1>Wechat</h1>
 *
 * <p>⚠️ 未实现：微信集成是占位空壳。当前只有 {@link #init()} 返回字符串 {@code "success"}，
 * 没有公众号/小程序登录、消息推送、回调处理、JS-SDK 签名等任何实质逻辑。见
 * {@code docs/业务功能未开发盘点.md} §1.2。</p>
 *
 * @author Hamm.cn
 */
@Permission(login = false)
@Controller
@RequestMapping("wechat")
public class WechatController extends ApiController {
    /**
     * {@code Success}
     */
    public static final String STRING_SUCCESS = "success";

    @RequestMapping(value = "init", produces = "text/plain")
    @ResponseBody
    public String init() {
        // 占位：微信 init 未实现。保留本端点仅为占位，不承载任何业务。
        return STRING_SUCCESS;
    }
}
