package cn.hamm.spms.framework.query;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * <h1>自持分页查询响应模型</h1>
 *
 * <p>纯 JDK 17 实现，无第三方注解或框架依赖，与前端分页响应格式完全兼容。</p>
 *
 * @param <T> 数据行实体类型
 * @author Hamm.cn
 */
public class QueryPageResponse<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private int total;
    private int pageCount;
    private List<T> list = new ArrayList<>();
    private Page page = new Page();
    private Sort sort;

    public QueryPageResponse() {
    }

    public QueryPageResponse(List<T> list, int total, int pageCount, Page page, Sort sort) {
        this.list = list != null ? list : new ArrayList<>();
        this.total = total;
        this.pageCount = pageCount;
        this.page = page != null ? page : new Page();
        this.sort = sort;
    }

    /**
     * 工厂方法：基于 {@link ReadPage} 计算结果与数据行构建自持分页响应。
     */
    public static <T> QueryPageResponse<T> of(ReadPage paging, List<T> rows, long totalElements, Sort requestedSort) {
        ReadPage.Counts counts = paging.counts(totalElements);
        return new QueryPageResponse<T>()
                .setList(rows != null ? rows : new ArrayList<>())
                .setTotal(counts.total())
                .setPageCount(counts.pageCount())
                .setPage(new Page(paging.pageNum(), paging.pageSize()))
                .setSort(requestedSort);
    }

    public int getTotal() {
        return total;
    }

    public QueryPageResponse<T> setTotal(int total) {
        this.total = total;
        return this;
    }

    public int getPageCount() {
        return pageCount;
    }

    public QueryPageResponse<T> setPageCount(int pageCount) {
        this.pageCount = pageCount;
        return this;
    }

    public List<T> getList() {
        return list;
    }

    public QueryPageResponse<T> setList(List<T> list) {
        this.list = list != null ? list : new ArrayList<>();
        return this;
    }

    public Page getPage() {
        return page;
    }

    public QueryPageResponse<T> setPage(Page page) {
        this.page = page != null ? page : new Page();
        return this;
    }

    public Sort getSort() {
        return sort;
    }

    public QueryPageResponse<T> setSort(Sort sort) {
        this.sort = sort;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueryPageResponse<?> that = (QueryPageResponse<?>) o;
        return total == that.total
                && pageCount == that.pageCount
                && Objects.equals(list, that.list)
                && Objects.equals(page, that.page)
                && Objects.equals(sort, that.sort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(total, pageCount, list, page, sort);
    }

    @Override
    public String toString() {
        return "QueryPageResponse{" +
                "total=" + total +
                ", pageCount=" + pageCount +
                ", list=" + list +
                ", page=" + page +
                ", sort=" + sort +
                '}';
    }
}
