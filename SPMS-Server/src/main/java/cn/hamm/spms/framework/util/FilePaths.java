package cn.hamm.spms.framework.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * <h1>本地上传所需的路径与写文件能力</h1>
 *
 * <p>只依赖 JDK，不复刻无调用的 ZIP、文件大小格式化等框架能力。
 * IO 异常保留原因交给 FileService 的既有错误边界处理。</p>
 *
 * @author Hamm.cn
 */
public final class FilePaths {
    public static final long FILE_SCALE = 1024L;

    private FilePaths() {
    }

    /**
     * 取最后一个点之后的后缀；无点时保留旧契约（整个文件名），合法性仍由上传白名单判断。
     * Locale.ROOT 避免服务器语言改变 ASCII 后缀大小写转换。
     */
    public static String getExtension(String fileName) {
        Objects.requireNonNull(fileName, "fileName");
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    /** 保持既有平台目录分隔符，不把本轮退场变成存量 URL/磁盘路径迁移。 */
    public static String formatDirectory(String directory) {
        Objects.requireNonNull(directory, "directory");
        return directory.endsWith(File.separator) ? directory : directory + File.separator;
    }

    /** 今日上传目录，按 JVM 默认时区，与既有 yyyyMMdd 目录一致。 */
    public static String getTodayDirectory() {
        return formatDirectory(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
    }

    /** 创建父目录并覆盖同名文件；覆盖时截断旧内容，保持哈希文件秒传/重复上传语义。 */
    public static void saveFile(String absoluteDirectory, String fileName, byte[] bytes) throws IOException {
        Path directory = Path.of(absoluteDirectory);
        if (!Files.exists(directory)) {
            try {
                Files.createDirectories(directory);
            } catch (IOException error) {
                throw new IOException("自动创建文件夹失败，" + error.getMessage(), error);
            }
        }
        try {
            // Files.write 默认 CREATE + TRUNCATE_EXISTING，免去 exists/createFile 间的竞态窗口。
            Files.write(directory.resolve(fileName), bytes);
        } catch (IOException error) {
            throw new IOException("文件保存失败，" + error.getMessage(), error);
        }
    }
}
