package cn.hamm.spms.common;

import cn.hamm.airpower.curd.config.CurdConfig;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * <h1>配置整合助手类</h1>
 *
 * @author Hamm.cn
 */
@Component
public class Configs {
    @Getter
    private static CurdConfig curdConfig;

    @Getter
    private static AppConfig appConfig;

    @Autowired
    private void initService(
            CurdConfig curdConfig,
            AppConfig appConfig
    ) {
        Configs.curdConfig = curdConfig;
        Configs.appConfig = appConfig;
    }
}
