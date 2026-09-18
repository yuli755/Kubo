package cn.hamm.spms.module.personnel.user.token;

import cn.hamm.spms.framework.curd.annotation.Unique;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.Desensitize;
import cn.hamm.spms.core.enums.DesensitizeType;
import cn.hamm.spms.framework.curd.annotation.Search;
import cn.hamm.spms.base.BaseEntity;
import cn.hamm.spms.module.personnel.user.UserEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;


/**
 * <h1>实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("personal_token")
@Description("私人令牌")
public class PersonalTokenEntity extends BaseEntity<PersonalTokenEntity> {
    @Description("令牌名称")
    @NotBlank(groups = {WhenUpdate.class, WhenAdd.class}, message = "令牌名称不能为空")
    @Search
    private String name;

    @Description("令牌")
    @Unique
    @Desensitize(value = DesensitizeType.CUSTOM, tail = 2, head = 6, replace = true, symbol = "**********")
    private String token;

    @Description("所属用户")
    @TableField(exist = false)
    private UserEntity user;

    /**
     * 外键列 user_id（MyBatis-Plus 侧的读写走这个标量字段）
     *
     * @apiNote 关系对象标了 {@code @TableField(exist = false)}（MyBatis-Plus 不把对象当列），
     * 这个标量字段才是列：写路径由 {@code RelationMapping#syncForeignKeys} 从关系对象同步过来，
     * 读路径由 {@code RelationHydrator} 按它批量回填关系对象。
     */
    @TableField("user_id")
    private Long userId;
}
