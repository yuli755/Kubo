package cn.hamm.spms.module.system.file;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Meta;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.ReadOnly;
import cn.hamm.spms.framework.curd.annotation.Search;
import cn.hamm.spms.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <h1>文件实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("file")
@Description("文件")
public class FileEntity extends BaseEntity<FileEntity> {
    @Description("名称")
    @Search
    @Meta
    private String name;

    @Description("扩展名")
    @Search
    @Meta
    private String extension;

    @Description("文件路径")
    @Search
    private String url;

    @Description("存储平台")
    private Integer platform;

    @Description("文件类别")
    private Integer category;

    @Description("MD5")
    @Search
    private String hashMd5;

    @Description("文件大小")
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
    private Long creatorId;
}
