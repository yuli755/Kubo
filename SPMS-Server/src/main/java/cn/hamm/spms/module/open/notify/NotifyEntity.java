package cn.hamm.spms.module.open.notify;

import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.core.annotation.Dictionary;
import cn.hamm.airpower.curd.annotation.Search;
import cn.hamm.spms.base.BaseEntity;
import cn.hamm.spms.module.open.notify.enums.NotifyChannel;
import cn.hamm.spms.module.open.notify.enums.NotifyScene;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.validator.constraints.Length;

/**
 * <h1>实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Data
@DynamicInsert
@DynamicUpdate
@Table(name = "notify")
@Description("通知")
public class NotifyEntity extends BaseEntity<NotifyEntity> {
    @Description("通知渠道")
    @Dictionary(value = NotifyChannel.class, groups = {WhenAdd.class, WhenUpdate.class})
    @Column(columnDefinition = "int UNSIGNED default 1 comment '通知渠道'")
    private Integer channel;

    @Description("通知场景")
    @Dictionary(value = NotifyScene.class, groups = {WhenAdd.class, WhenUpdate.class})
    @Column(columnDefinition = "int UNSIGNED default 1 comment '通知场景'")
    private Integer scene;

    @Description("通知地址")
    @Column(columnDefinition = "varchar(255) default '' comment '通知地址'")
    private String url;

    /**
     * 只进不出：webhook 令牌经 getPage/get 回显等于把「向该 webhook 投递消息」的
     * 能力转授给任何有 notify 查询权限的人（读侧脱敏，写侧不受影响）
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Description("通知令牌")
    @Column(columnDefinition = "varchar(255) default '' comment '通知令牌'")
    private String token;

    @Description("备注信息")
    @Search
    @Column(columnDefinition = "text comment '备注信息'")
    @Length(max = 1000, message = "备注信息最多允许{max}个字符")
    private String remark;
}
