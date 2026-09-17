package cn.hamm.spms.module.system.config;

import cn.hamm.spms.core.DictionaryUtil;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.base.spi.SystemConfigSwitch;
import cn.hamm.spms.base.config.ConfigFlag;
import cn.hamm.spms.base.config.ConfigType;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import static cn.hamm.spms.common.exception.Errors.DATA_NOT_FOUND;
import static cn.hamm.spms.common.exception.Errors.FORBIDDEN_DELETE;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
public class ConfigService extends BaseService<ConfigEntity, ConfigRepository> implements SystemConfigSwitch {
    /**
     * String {@code "0"}
     */
    public static final String STRING_ZERO = "0";
    /**
     * String {@code "1"}
     */
    public static final String STRING_ONE = "1";

    /**
     * 该开关是否处于开启状态（{@link cn.hamm.spms.base.spi.SystemConfigSwitch} 的实现）
     *
     * <p>与 {@link #get(ConfigFlag)} 的区别只有一个：这里把"开着吗"回答成一个布尔值，
     * 供底座（{@code base/bill/AbstractBaseBillService}）在不认识 {@link ConfigEntity} 的前提下使用。
     * 配置缺失仍然抛错 —— 那不是"开关关了"，而是环境没初始化好。</p>
     *
     * @param flag 配置标识
     * @return 是否开启
     */
    @Override
    public boolean isEnabled(@NotNull ConfigFlag flag) {
        return Boolean.TRUE.equals(get(flag).booleanConfig());
    }

    /**
     * 根据配置枚举获取配置信息
     *
     * @param configFlag 配置枚举
     * @return 配置信息
     */
    public final ConfigEntity get(@NotNull ConfigFlag configFlag) {
        return get(configFlag.name());
    }

    /**
     * 根据配置标识获取配置信息
     *
     * @param flag 配置标识
     * @return 配置信息
     */
    public final ConfigEntity get(@NotNull String flag) {
        ConfigEntity configuration = repository.getByFlag(flag);
        DATA_NOT_FOUND.whenNull(configuration, "查询配置失败");
        return configuration;
    }

    /**
     * 根据配置标识获取配置信息（不存在时返回 {@code null}，不抛错）。
     *
     * <p>供功能开关/灰度等「配置可缺失、缺失时走默认值」的场景使用（见
     * {@link cn.hamm.spms.common.feature.FeatureFlagService}）。</p>
     *
     * @param flag 配置标识
     * @return 配置信息，或 {@code null}
     */
    public final ConfigEntity getOrNull(@NotNull String flag) {
        return repository.getByFlag(flag);
    }

    @Override
    protected void beforeDelete(@NotNull ConfigEntity config) {
        FORBIDDEN_DELETE.when(config.getIsSystem(), "系统内置配置无法被删除!");
    }

    @Override
    protected ConfigEntity beforeAppSaveToDatabase(@NotNull ConfigEntity config) {
        String key = config.getFlag();
        config.setIsSystem(false);
        // 如果 Configuration枚举中包含这个标识 则设置为系统标识
        for (ConfigFlag configFlag : ConfigFlag.values()) {
            if (configFlag.name().equals(key)) {
                config.setIsSystem(true);
                ConfigType type = DictionaryUtil.getDictionary(ConfigType.class, config.getType());
                switch (type) {
                    case BOOLEAN:
                        config.setConfig(STRING_ONE.equals(config.getConfig()) ?
                                STRING_ONE :
                                STRING_ZERO
                        );
                        break;
                    case NUMBER:
                        config.setConfig(
                                Long.valueOf(config.getConfig()).toString()
                        );
                        break;
                    default:
                }
                break;
            }
        }
        return config;
    }
}
