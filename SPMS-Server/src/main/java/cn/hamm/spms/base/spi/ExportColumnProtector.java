package cn.hamm.spms.base.spi;

import java.util.List;

/**
 * <h1>底座向业务域要的能力：导出内容的列级遮蔽</h1>
 *
 * <p>框架的导出行是"反射取字段值 → 拼 CSV"，全程<b>不经过 Jackson</b>，所以序列化层那套
 * 掩码对导出文件无效 —— 界面显示 {@code 138****000}、导出的文件里是明文，是这类系统最常见的
 * 一次事故。base 能插手的唯一位置是 {@code CurdService.afterExportQuery}（由 {@code javap} 确认：
 * {@code createExportTask} 是 final、{@code queryExportList} 是 private），
 * 于是"这一批行该怎么遮蔽"作为能力向上要。</p>
 *
 * <p><b>实现必须只改副本</b>：导出查询返回的实体可能仍在本事务的持久化上下文里，
 * 就地 {@code field.set(entity, mask)} 等于把掩码写进数据库。</p>
 *
 * @author Hamm.cn
 */
public interface ExportColumnProtector {
    /**
     * 对一批待导出的实体做列级遮蔽
     *
     * @param entities 导出查询拿到的实体列表（可能为 {@code null} / 空）
     * @param <E>      实体类型
     * @return 遮蔽后的列表；无需处理时原样返回入参引用（不复制、不新建列表）
     */
    <E> List<E> protectForExport(List<E> entities);
}
