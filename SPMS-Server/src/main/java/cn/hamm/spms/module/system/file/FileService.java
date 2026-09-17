package cn.hamm.spms.module.system.file;

import cn.hamm.airpower.core.FileUtil;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.common.AppConfig;
import cn.hamm.spms.module.system.file.enums.FileCategory;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import static cn.hamm.spms.common.exception.Errors.FORBIDDEN_UPLOAD_MAX_SIZE;
import static cn.hamm.spms.common.exception.Errors.PARAM_INVALID;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
@Slf4j
public class FileService extends BaseService<FileEntity, FileRepository> {
    @Autowired
    private AppConfig appConfig;

    /**
     * 文件上传
     *
     * @param multipartFile 文件
     * @param fileCategory  文件类别
     * @param creatorId     上传人ID（匿名上传为 null）
     * @return 存储的文件信息
     */
    public FileEntity upload(@NotNull MultipartFile multipartFile, @NotNull FileCategory fileCategory, Long creatorId) {
        // 上传文件的绝对路径
        final String absoluteDirectory = appConfig.getFileDirectory();
        // 判断文件大小和类型
        FORBIDDEN_UPLOAD_MAX_SIZE.when(multipartFile.getSize() > appConfig.getUploadMaxSize());
        String originalFilename = multipartFile.getOriginalFilename();
        PARAM_INVALID.whenNull(originalFilename, "文件名不能为空");
        String extension = FileUtil.getExtension(originalFilename);
        PARAM_INVALID.whenEmpty(extension, "文件类型不能为空");
        PARAM_INVALID.when(!Arrays.stream(fileCategory.getExtensions()).toList().contains(extension), "文件类型不允许上传");

        // 存储的相对路径目录
        String relativeDirectory = appConfig.getUploadDirectory() + fileCategory.name().toLowerCase() + "/" + FileUtil.getTodayDirectory();

        try {
            // 获取文件的MD5
            String hashMd5 = DigestUtils.md5DigestAsHex(multipartFile.getBytes());
            // 阶段 D-4：秒传只在同一属主内复用。跨用户直接返回他人文件行等于
            // 泄露他人上传过的文件名与存在性（构造同内容探针即可确认），且让两条
            // 上传链路共享同一行的生命周期。未命中时新建行；物理文件按
            // hashMd5 命名，重复落盘是幂等覆盖，无副作用。
            FileEntity file = repository.getListByCategoryAndHashMd5(fileCategory.getKey(), hashMd5).stream()
                    .filter(exist -> Objects.equals(exist.getCreatorId(), creatorId))
                    .findFirst()
                    .orElse(null);
            if (Objects.nonNull(file)) {
                return file;
            }

            // 文件名
            String fileName = hashMd5 + "." + extension;

            // 保存的相对文件路径
            String relativeFilePath = relativeDirectory + fileName;

            // 只有本地存储一种落地方式：对象存储（阿里云 OSS）已随栈外依赖一并移除，
            // 生产配置本来就是 `upload-platform: local`（application-production.yml）。
            // 将来要上对象存储，用 S3 协议客户端（MinIO/OSS 都兼容）重开一个分支，
            // 而不是把云厂商 SDK 再引回来。
            FileUtil.saveFile(absoluteDirectory + relativeDirectory, fileName, multipartFile.getBytes());

            file = new FileEntity().setExtension(extension)
                    .setSize(multipartFile.getSize())
                    .setPlatform(appConfig.getUploadPlatform().getKey())
                    .setCategory(fileCategory.getKey())
                    .setName(multipartFile.getOriginalFilename())
                    .setHashMd5(hashMd5)
                    .setCreatorId(creatorId)
                    .setUrl(relativeFilePath);
            return addAndGet(file);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ServiceException("上传文件失败，" + e.getMessage());
        }
    }
}
