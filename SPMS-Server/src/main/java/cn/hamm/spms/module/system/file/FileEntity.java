package cn.hamm.spms.module.system.file;

import cn.hamm.airpower.core.annotation.Meta;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.core.annotation.ReadOnly;
import cn.hamm.airpower.curd.annotation.Search;
import cn.hamm.spms.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/**
 * <h1>文件实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Data
@DynamicInsert
@DynamicUpdate
@Table(name = "file")
@Description("文件")
public class FileEntity extends BaseEntity<FileEntity> {
    @Description("名称")
    @Search
    @Column(columnDefinition = "varchar(255) default '' comment '名称'")
    @Meta
    private String name;

    @Description("扩展名")
    @Search
    @Column(columnDefinition = "varchar(255) default '' comment '扩展名'")
    @Meta
    private String extension;

    @Description("文件路径")
    @Search
    @Column(columnDefinition = "varchar(255) default '' comment '文件路径'")
    private String url;

    @Description("存储平台")
    @Column(columnDefinition = "int UNSIGNED default 0 comment '存储平台'")
    private Integer platform;

    @Description("文件类别")
    @Column(columnDefinition = "int UNSIGNED default 1 comment '文件类别'")
    private Integer category;

    @Description("MD5")
    @Search
    @Column(columnDefinition = "varchar(255) default '' comment 'MD5'")
    private String hashMd5;

    @Description("文件大小")
    @Column(columnDefinition = "bigint UNSIGNED default 0 comment '文件大小'")
    @Meta
    private Long size;

    /**
     * 上传人（阶段 D-4）。
     *
     * <p>FileEntity 继承 BaseEntity（没有 TenantAwareEntity 的 creatorId 填充），
     * 此前文件行完全没有属主信息：MD5 秒传跨用户返回他人文件行（泄露他人文件名/
     * 存在性），file/delete 也无从校验归属。上传时从登录态显式落库；
     * 历史行为 null，删除时仅 root 可清理。ReadOnly：防止客户端在 add/update
     * 报文里伪造属主。</p>
     */
    @Description("上传人ID")
    @ReadOnly
    @Column(name = "creator_id", columnDefinition = "bigint UNSIGNED default null comment '上传人ID'")
    private Long creatorId;
}
