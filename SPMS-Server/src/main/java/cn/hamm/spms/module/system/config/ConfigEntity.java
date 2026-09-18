package cn.hamm.spms.module.system.config;

import cn.hamm.spms.framework.curd.annotation.Unique;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.Dictionary;
import cn.hamm.spms.core.annotation.Meta;
import cn.hamm.spms.core.annotation.ReadOnly;
import cn.hamm.spms.base.BaseEntity;
import cn.hamm.spms.base.config.ConfigType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.Objects;

import static cn.hamm.spms.module.system.config.ConfigService.STRING_ONE;
import static com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY;

/**
 * <h1>配置信息实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("config")
@Description("配置信息")
public class ConfigEntity extends BaseEntity<ConfigEntity> {
    @Description("配置标识")
    @Unique
    @NotBlank(groups = {WhenUpdate.class, WhenAdd.class}, message = "配置标识不能为空")
    @Meta
    private String flag;

    @Description("配置名称")
    @NotBlank(groups = {WhenUpdate.class, WhenAdd.class}, message = "配置名称不能为空")
    @Meta
    private String name;

    @Description("配置描述")
    private String description;

    @Description("配置的值")
    @Meta
    private String config;

    @Description("配置类型")
    @Dictionary(value = ConfigType.class, groups = {WhenAdd.class, WhenUpdate.class})
    @Meta
    private Integer type;

    @Description("内置配置")
    @ReadOnly
    private Boolean isSystem;

    /**
     * 设置是系统内置配置
     *
     * @param isSystem 内置配置
     * @return 配置信息
     */
    public ConfigEntity setIsSystem(Boolean isSystem) {
        this.isSystem = isSystem;
        return this;
    }

    @TableField(exist = false)
    @JsonProperty(access = WRITE_ONLY)
    public Boolean booleanConfig() {
        if (Objects.isNull(config)) {
            return false;
        }
        return STRING_ONE.equals(config);
    }

    @TableField(exist = false)
    public Long numberConfig() {
        if (Objects.isNull(config)) {
            return 0L;
        }
        return Long.parseLong(config);
    }
}
