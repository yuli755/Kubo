package cn.hamm.spms.module.personnel.user;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.module.system.datapermission.annotation.SensitiveField;

import cn.hamm.spms.core.annotation.*;
import cn.hamm.spms.core.enums.DesensitizeType;
import cn.hamm.spms.framework.curd.annotation.Search;
import cn.hamm.spms.base.TenantAwareEntity;
import cn.hamm.spms.module.factory.storage.StorageEntity;
import cn.hamm.spms.module.personnel.department.DepartmentEntity;
import cn.hamm.spms.module.personnel.role.RoleEntity;
import cn.hamm.spms.module.personnel.user.enums.UserGender;
import cn.hamm.spms.module.system.datapermission.SystemPermissionIdentities;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.validator.constraints.Length;


import java.util.Set;

import static com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY;

/**
 * <h1>用户实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Data
@DynamicInsert
@DynamicUpdate
@TableName("user")
@Table(name = "user")
@Description("用户")
public class UserEntity extends TenantAwareEntity<UserEntity> implements IUserAction {
    @Description("用户昵称")
    @Column(columnDefinition = "varchar(255) default '' comment '昵称'")
    @NotBlank(groups = {WhenUpdate.class, WhenAdd.class, WhenUpdateMyInfo.class}, message = "昵称不能为空")
    @Search
    @Meta
    private String nickname;

    @Description("头像")
    @Column(columnDefinition = "varchar(255) default '' comment '头像'")
    private String avatar;

    @Description("真实姓名")
    @Desensitize(DesensitizeType.CHINESE_NAME)
    @Column(columnDefinition = "varchar(255) default '' comment '真实姓名'")
    private String realName;

    @Description("身份证号")
    @Desensitize(DesensitizeType.ID_CARD)
    /**
     * 身份证号：策略驱动脱敏。未配 data_field_policy 时输出原值（零行为变更），
     * 配了才遮 —— 与 price 同一套机制，前端列表默认打码只是"防瞄屏"，不是权限
     */
    @SensitiveField(resourceKey = "personnel.user")
    @Column(columnDefinition = "varchar(255) default '' comment '身份证号'")
    private String idCard;

    @Description("邮箱")
    @SensitiveField(resourceKey = "personnel.user")
    // 唯一列严禁 default ''（审核项 A-3）：Hibernate 动态 INSERT 会省略 null 字段，MySQL 随即补列默认值——
    // 默认值若是 '' 就等于"每个未填邮箱的用户都抢占同一个空串"，第二个未填邮箱的用户必撞唯一键，
    // 且 DataIntegrityViolation 被框架报成「删除失败，数据正在使用中」，排查极易跑错方向。
    // NULL 在唯一索引里天然允许多行共存，才是"未填写"的正确表示。存量数据修正见
    // docs/database/migration/20260915_user_email_phone_unique_null_default.sql。
    @Column(columnDefinition = "varchar(255) comment '邮箱'", unique = true)
    @NotBlank(groups = {WhenSendEmail.class}, message = "邮箱不能为空")
    @Email(groups = {WhenResetMyPassword.class, WhenSendEmail.class}, message = "邮箱格式不正确")
    @Search
    private String email;

    @Description("手机号")
    @SensitiveField(resourceKey = "personnel.user")
    // 同 email：unique + default '' 会让第二个未填手机号的用户无法创建（A-3）
    @Column(columnDefinition = "varchar(255) comment '手机号'", unique = true)
    @Phone(groups = {WhenResetMyPassword.class, WhenSendSms.class}, message = "手机格式不正确")
    @Search
    private String phone;

    @Description("性别")
    @Dictionary(value = UserGender.class, groups = {WhenAdd.class, WhenUpdate.class})
    @Column(columnDefinition = "int UNSIGNED default 0 comment '性别'")
    private Integer gender;

    @JsonProperty(access = WRITE_ONLY)
    @Description("密码")
    @Column(columnDefinition = "varchar(255) default '' comment '密码'")
    @NotBlank(groups = {WhenLogin.class, WhenResetMyPassword.class, WhenUpdateMyPassword.class}, message = "密码不能为空")
    @Null(groups = {WhenUpdateMyInfo.class}, message = "请勿传入 Password 字段")
    @Length(min = 6, message = "密码至少6位长度")
    private String password;

    @Description("密码盐")
    @JsonIgnore
    @Column(columnDefinition = "varchar(255) default '' comment '密码盐'")
    private String salt;

    /// ////////////////////

    @Description("邮箱验证码")
    @NotBlank(groups = {WhenResetMyPassword.class}, message = "邮箱验证码不能为空")
    @Transient
    private String code;

    @Description("原始密码")
    @NotBlank(groups = {WhenUpdateMyPassword.class}, message = "原始密码不能为空")
    @Transient
    private String oldPassword;

    @Description("部门 ID 查询")
    @Transient
    private Long departmentId;

    @ManyToMany(fetch = FetchType.LAZY)
    private Set<RoleEntity> roleList;

    @Description("部门列表")
    @ManyToMany(fetch = FetchType.LAZY)
    private Set<DepartmentEntity> departmentList;

    @Description("仓库授权列表")
    @ManyToMany(fetch = FetchType.LAZY)
    private Set<StorageEntity> storageList;

    @Description("工厂授权列表（工厂 = 仓库/工厂树的顶层节点，D13）")
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_factory",
            joinColumns = @JoinColumn(name = "user_entity_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "factory_id", referencedColumnName = "id")
    )
    private Set<StorageEntity> factoryList;

    /**
     * 获取是否超级管理员
     *
     * <p>禁用态不参与：与 {@code SystemPermissionService.has} 同口径——停用携带
     * {@code system:root} 的角色必须真实地摘掉 root（root 是所有校验的旁路开关，
     * 「禁用即失效」不成立时收回超管只能靠删行，恢复语义也全部落空）。</p>
     *
     * @return 结果
     */
    @Transient
    @JsonIgnore
    public final boolean isRootUser() {
        return getRoleList() != null
                && getRoleList().stream()
                .filter(role -> role != null && !Boolean.TRUE.equals(role.getIsDisabled()))
                .flatMap(role -> role.getPermissionList() == null ? java.util.stream.Stream.empty() : role.getPermissionList().stream())
                .anyMatch(permission -> permission != null
                        && !Boolean.TRUE.equals(permission.getIsDisabled())
                        && SystemPermissionIdentities.ROOT.equals(permission.getIdentity()));
    }
}


