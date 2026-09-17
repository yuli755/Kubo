package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.base.spi.ExportColumnProtector;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * <h1>导出列遮蔽的 SPI 适配器</h1>
 *
 * <p>遮蔽判定与复制语义全在 {@link ExportColumnMasker}（静态工具，有单测），本类只做转发。
 * 适配器而不是让 {@code ExportColumnMasker} 自己去实现接口，是因为它的私有构造与
 * "纯静态工具"这一形状本身有价值：不能被替换、不能被 mock 掉。</p>
 *
 * <p><b>必须是常驻 Bean</b>：不许加条件装配。它缺席时导出会退回"导出文件里是明文、
 * 界面显示掩码"的形状 —— 这类事故在日志里看不出任何异常。</p>
 *
 * @author Hamm.cn
 */
@Component
public class ExportColumnProtectorAdapter implements ExportColumnProtector {
    @Override
    public <E> List<E> protectForExport(List<E> entities) {
        return ExportColumnMasker.maskForExport(entities);
    }
}
