package cn.hamm.spms.common.export;

import cn.hamm.spms.common.metadata.LegacyEntityMetadata;
import cn.hamm.spms.framework.util.CsvWriter;

import java.io.InputStream;
import java.util.List;

/**
 * <h1>应用实体到自持 CSV 编码器的出口</h1>
 *
 * <p>此处不查询数据、不决定权限、不偷偷扩大导出字段。
 * 调用方必须先完成取数与列级脱敏；异步下载任务仍由现有 ExportHelper 管理。</p>
 *
 * @author Hamm.cn
 */
public final class EntityCsvExport {
    private EntityCsvExport() {
    }

    /** 注解只在一批导出开始时解析一次，不在逐行编码时重复扫描。 */
    public static <T> InputStream toCsvInputStream(List<T> rows, Class<T> type) {
        return CsvWriter.toInputStream(rows, LegacyEntityMetadata.exportColumns(type));
    }
}
