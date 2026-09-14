package cn.hamm.spms.module.system.file;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.core.DictionaryUtil;
import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.curd.permission.Permission;
import cn.hamm.spms.base.BaseController;
import cn.hamm.spms.module.personnel.user.UserService;
import cn.hamm.spms.module.system.file.enums.FileCategory;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

import static cn.hamm.airpower.exception.Errors.FORBIDDEN_EDIT;

/**
 * <h1>Controller</h1>
 *
 * @author Hamm.cn
 */
@Api("file")
@Description("文件")
public class FileController extends BaseController<FileEntity, FileService, FileRepository> {

    @Autowired
    private UserService userService;

    @PostMapping("upload")
    @Description("文件上传")
    @Permission(authorize = false)
    public Json upload(@NotNull(message = "文件不能为空") @RequestParam("file") MultipartFile file, @RequestParam("category") Integer category) {
        category = Objects.requireNonNullElse(category, FileCategory.NORMAL.getKey());
        FileCategory fileCategory = DictionaryUtil.getDictionary(FileCategory.class, category);
        // 该接口 authorize=false 允许匿名，登录用户上传时把属主落库（D-4）；
        // 匿名（无头/坏头）时 getCurrentUserId 校验失败，按无属主记录。
        Long creatorId = null;
        try {
            creatorId = getCurrentUserId();
        } catch (RuntimeException ignored) {
            // 匿名上传：保持 authorize=false 的既有契约，creator_id 记 null
        }
        return Json.data(service.upload(file, fileCategory, creatorId), "文件上传成功");
    }

    /**
     * 阶段 D-5：文件删除此前无归属校验，拿到 id 即可删任意文件行。
     * 属主放行；历史/匿名行（creator_id 为 null）与越权删除仅 root 可执行。
     * 注意：参数注解必须用 org.jetbrains 的 @NotNull（与父类一致）——
     * 这里若换成 jakarta 的 @NotNull，会被 Bean Validation 识别为「重写方法新增
     * 参数约束」，整个 FileController 的请求直接 HV000151 500。
     */
    @Override
    protected void beforeAppDelete(@org.jetbrains.annotations.NotNull FileEntity file) {
        Long creatorId = file.getCreatorId();
        long currentUserId = getCurrentUserId();
        if (Objects.equals(creatorId, currentUserId)) {
            return;
        }
        FORBIDDEN_EDIT.when(!userService.get(currentUserId).isRootUser(), "你没有权限删除此文件");
    }
}
