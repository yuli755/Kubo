package cn.hamm.spms.common.export;

import cn.hamm.spms.common.AppConfig;
import cn.hamm.spms.common.redis.RedisHelper;
import cn.hamm.spms.framework.error.BusinessException;
import cn.hamm.spms.framework.util.FilePaths;
import cn.hamm.spms.framework.util.RandomValues;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.scheduling.annotation.Scheduled;

import static cn.hamm.spms.common.exception.Errors.DATA_NOT_FOUND;

/**
 * <h1>导出文件帮助类</h1>
 *
 * <p>自持异步导出任务管理与文件落盘，替代旧 {@code cn.hamm.airpower.curd.helper.ExportHelper}。</p>
 * <p>具备 ThreadLocal 数据权限上下文（{@link PermissionContextHolder}）与 MDC 日志追踪链路向异步线程池的自动穿透能力，
 * 保证导出文件在异步组装落盘时严格遵守列级数据脱敏与审计策略。</p>
 *
 * @author Hamm.cn
 */
@Component
@Slf4j
public class ExportHelper {
    public static final String CSV_ROW_DELIMITER = "\r\n";
    private static final String EXPORT_DIR = "export";
    private static final String EXPORT_CACHE_PREFIX = EXPORT_DIR + "_";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private RedisHelper redisHelper;

    @Autowired(required = false)
    private ExportConfig exportConfig;

    @Autowired(required = false)
    private AppConfig appConfig;

    /**
     * 保存 CSV 数据行到文件
     *
     * @param exportFile 目标导出文件
     * @param valueList  数据列表
     */
    public static void saveCsvListToFile(@NotNull ExportFile exportFile, List<String> valueList) {
        String rowString = String.join(",", valueList);
        try {
            Path dir = Path.of(exportFile.getAbsoluteDirectory());
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Files.writeString(dir.resolve(exportFile.getFileName()), rowString + CSV_ROW_DELIMITER,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("保存 CSV 行失败", e);
            throw new BusinessException("保存 CSV 行失败: " + e.getMessage());
        }
    }

    /**
     * 创建异步导出任务（具备数据权限与日志追踪上下文穿透）
     *
     * @param supplier 异步执行导出并返回文件路径的回调
     * @return 任务编码 fileCode
     */
    public final String createExportTask(Supplier<String> supplier) {
        String fileCode = RandomValues.randomString().toLowerCase();
        final String fileCacheKey = EXPORT_CACHE_PREFIX + fileCode;
        Object object = redisHelper.get(fileCacheKey);
        if (Objects.nonNull(object)) {
            return createExportTask(supplier);
        }
        long taskTtlSeconds = taskTtlSeconds();
        redisHelper.set(fileCacheKey, "", taskTtlSeconds);

        // 捕获请求线程的数据权限上下文与 MDC 链路追踪上下文，向异步线程池穿透
        final PermissionContext capturedContext = PermissionContextHolder.current().orElse(null);
        final Map<String, String> capturedMdc = MDC.getCopyOfContextMap();

        CompletableFuture.runAsync(() -> {
            if (capturedMdc != null) {
                MDC.setContextMap(capturedMdc);
            }
            try (PermissionContextHolder.Scope ignored = (capturedContext != null
                    ? PermissionContextHolder.open(capturedContext)
                    : () -> {})) {
                try {
                    String result = supplier.get();
                    redisHelper.set(fileCacheKey, result != null ? result : "", taskTtlSeconds);
                } catch (Exception e) {
                    log.error("执行导出任务失败", e);
                    redisHelper.set(fileCacheKey, "ERROR: " + e.getMessage(), taskTtlSeconds);
                }
            } finally {
                MDC.clear();
            }
        });
        return fileCode;
    }

    private long taskTtlSeconds() {
        long configured = exportConfig != null ? exportConfig.getTaskTtlSeconds() : 3600L;
        return Math.max(1L, configured);
    }

    /**
     * 定期清理已过期的导出文件。Redis 任务状态到期后，磁盘文件也必须有明确的保留上限。
     *
     * <p>只扫描配置的文件根目录下的 {@code export/} 子目录，且只删除超过保留期的普通文件；
     * 目录、根目录之外的文件和最近文件均不触碰。清理失败只影响存储回收，不影响请求链路，下一轮会重试。</p>
     */
    @Scheduled(cron = "${app.export.file-retention-cleanup-cron:0 30 3 * * *}")
    public void purgeExpiredFiles() {
        int retentionDays = exportConfig != null ? exportConfig.getFileRetentionDays() : 1;
        if (retentionDays <= 0) {
            log.error("[export][retention] file-retention-days 必须为正数，已跳过本次清理：{}", retentionDays);
            return;
        }

        String configuredRoot = exportConfig != null ? exportConfig.getExportPath() : null;
        if (!StringUtils.hasText(configuredRoot)) {
            configuredRoot = appConfig != null ? appConfig.getFileDirectory() : "/home/static/";
        }
        Path exportRoot = Path.of(FilePaths.formatDirectory(configuredRoot)).resolve(EXPORT_DIR).normalize();
        if (!Files.isDirectory(exportRoot)) {
            return;
        }

        Instant cutoff = Instant.now().minus(Period.ofDays(retentionDays));
        int deleted = 0;
        try (Stream<Path> paths = Files.walk(exportRoot)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                FileTime modified = Files.getLastModifiedTime(path);
                if (modified.toInstant().isBefore(cutoff)) {
                    Files.deleteIfExists(path);
                    deleted++;
                }
            }
        } catch (IOException e) {
            log.warn("[export][retention] 清理过期导出文件失败，下一轮重试：{}", e.getMessage());
            return;
        }
        if (deleted > 0) {
            log.info("[export][retention] 清理过期导出文件：{} 个，保留 {} 天，目录 {}", deleted, retentionDays, exportRoot);
        }
    }

    /**
     * 获取导出文件 URL
     *
     * @param fileCode 任务编码
     * @return 文件路径/URL
     */
    public final String getExportFileUrl(String fileCode) {
        Object object = redisHelper.get(EXPORT_CACHE_PREFIX + fileCode);
        DATA_NOT_FOUND.whenEmpty(object, "文件暂未准备完毕");
        return object.toString();
    }

    /**
     * 保存导出文件流为 CSV
     *
     * @param inputStream 输入流
     * @return 保存后的相对路径
     */
    public final @NotNull String saveExportFileStream(InputStream inputStream) {
        return saveExportFileStream(inputStream, "csv");
    }

    /**
     * 保存导出文件流为指定扩展名文件
     *
     * @param inputStream 输入流
     * @param extension   文件扩展名
     * @return 保存后的相对路径
     */
    public final @NotNull String saveExportFileStream(@NotNull InputStream inputStream, String extension) {
        ExportFile exportFile = getExportFilePath(extension);
        try {
            FilePaths.saveFile(exportFile.getAbsoluteDirectory(), exportFile.getFileName(), inputStream.readAllBytes());
        } catch (IOException e) {
            log.error("保存导出的文件失败", e);
            throw new BusinessException("保存导出的文件失败，" + e.getMessage());
        }
        return exportFile.getRelativeFile();
    }

    /**
     * 获取导出文件路径结构
     *
     * @param extension 文件扩展名
     * @return 导出文件信息
     */
    public final @NotNull ExportFile getExportFilePath(String extension) {
        String exportRootDirectory = exportConfig != null ? exportConfig.getExportPath() : null;
        if (!StringUtils.hasText(exportRootDirectory)) {
            exportRootDirectory = appConfig != null ? appConfig.getFileDirectory() : "/home/static/";
        }
        exportRootDirectory = FilePaths.formatDirectory(exportRootDirectory);

        String relativeDirectory = FilePaths.formatDirectory("export/" + FilePaths.getTodayDirectory());
        String fileName = LocalDateTime.now().format(TIME_FORMATTER) + "_" + RandomValues.randomString(8) + "." + extension;

        return new ExportFile()
                .setExportRootDirectory(exportRootDirectory)
                .setRelativeDirectory(relativeDirectory)
                .setFileName(fileName);
    }

    @Setter
    @Accessors(chain = true)
    public static class ExportFile {
        private String exportRootDirectory;

        @Getter
        private String fileName;

        private String relativeDirectory;

        public String getAbsoluteDirectory() {
            return exportRootDirectory + relativeDirectory;
        }

        public String getRelativeFile() {
            return relativeDirectory + fileName;
        }
    }
}
