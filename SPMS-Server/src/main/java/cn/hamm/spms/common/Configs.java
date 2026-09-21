package cn.hamm.spms.common;

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
    private static AppConfig appConfig;

    @Autowired
    private void initService(
            AppConfig appConfig
    ) {
        Configs.appConfig = appConfig;
    }
}
