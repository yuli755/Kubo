package cn.hamm.spms.framework.curd.model.query;

/*
 * 来源：AirPower4J 6.4.3（https://github.com/AirPowerTeam/AirPower4J ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场 —【框架层自持】整模块搬入本仓，只改包名：
 *   cn.hamm.airpower.core  → cn.hamm.spms.core
 *   cn.hamm.airpower.api   → cn.hamm.spms.framework.api
 *   cn.hamm.airpower.curd  → cn.hamm.spms.framework.curd
 *   cn.hamm.airpower.redis → cn.hamm.spms.framework.redis
 * 代码逻辑一字未改（JPA→MyBatis-Plus 的持久化替换是紧随其后的独立一批，见 docs）。
 * 从此这一层由本仓拥有：改代码改这里，不再有"上游"。
 */

import cn.hamm.spms.core.RootModel;
import cn.hamm.spms.core.annotation.Description;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>分页数据</h1>
 *
 * @param <M> 模型
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
public class PageData<M extends RootModel<M>> extends RootModel<M> {
    /**
     * 总数量
     */
    @Description("总数量")
    private int total = 0;

    /**
     * 总页数
     */
    @Description("总页数")
    private int pageCount = 0;

    /**
     * 数据信息
     */
    @Description("数据列表")
    private List<M> list = new ArrayList<>();

    /**
     * 分页信息
     */
    @Description("分页信息")
    private Page page = new Page();

    /**
     * 获取新的实例
     *
     * @param page MyBatis-Plus 分页数据
     * @param <M>  实体类型
     * @return 实例
     * @apiNote 2026-09-18 J2：参数类型从 Spring Data 的 {@code Page} 换成 MyBatis-Plus 的 {@code IPage}。
     * {@code IPage.current} 从 1 开始，与 {@link Page#getPageNum()} 同口径，**不再 +1**
     * （Spring Data 那版要 +1 是因为它的页码从 0 起，照抄会把页码整体错一位）。
     */
    public static <M extends RootModel<M>> @NotNull PageData<M> newInstance(com.baomidou.mybatisplus.core.metadata.@NotNull IPage<M> page) {
        PageData<M> pageData = new PageData<>();
        pageData.setList(page.getRecords())
                .setTotal(Math.toIntExact(page.getTotal()))
                .setPageCount(Math.toIntExact(page.getPages()))
                .setPage(new Page()
                        .setPageSize(Math.toIntExact(page.getSize()))
                        .setPageNum(Math.toIntExact(page.getCurrent()))
                );
        return pageData;
    }
}
