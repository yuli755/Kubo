package cn.hamm.spms.module.system;

import cn.hamm.spms.base.ApiController;
import cn.hamm.spms.framework.web.annotation.Api;
import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.framework.web.annotation.Permission;
import cn.hamm.spms.common.redis.RedisHelper;
import cn.hamm.spms.module.asset.material.MaterialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * <h1>Controller</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Permission
@Api("/")
@Description("首页")
public class IndexController extends ApiController {
    @Autowired
    private RedisHelper redisHelper;
    @Autowired
    private MaterialService materialService;

    @GetMapping("")
    public String index() {
        long index = redisHelper.increment("index");
        return "<h1>Server running! " + index + "</h1>";
    }

}
