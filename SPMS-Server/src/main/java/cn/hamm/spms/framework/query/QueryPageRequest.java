package cn.hamm.spms.framework.query;

import java.util.Objects;

/**
 * <h1>自持分页查询请求模型</h1>
 *
 * <p>纯 JDK 17 实现，继承 {@link QueryListRequest}，补充默认的分页参数。</p>
 *
 * @param <T> 实体类型
 * @author Hamm.cn
 */
public class QueryPageRequest<T> extends QueryListRequest<T> {
    private static final long serialVersionUID = 1L;

    private Page page = new Page();

    public QueryPageRequest() {
    }

    public QueryPageRequest(T filter) {
        super(filter);
    }

    public QueryPageRequest(T filter, Page page) {
        super(filter);
        this.page = page != null ? page : new Page();
    }

    public QueryPageRequest(T filter, Page page, Sort sort) {
        super(filter, sort);
        this.page = page != null ? page : new Page();
    }

    public Page getPage() {
        return page;
    }

    public QueryPageRequest<T> setPage(Page page) {
        this.page = page != null ? page : new Page();
        return this;
    }

    @Override
    public QueryPageRequest<T> setSort(Sort sort) {
        super.setSort(sort);
        return this;
    }

    @Override
    public QueryPageRequest<T> setFilter(T filter) {
        super.setFilter(filter);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        QueryPageRequest<?> that = (QueryPageRequest<?>) o;
        return Objects.equals(page, that.page);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), page);
    }

    @Override
    public String toString() {
        return "QueryPageRequest{" +
                "filter=" + getFilter() +
                ", sort=" + getSort() +
                ", page=" + page +
                '}';
    }
}
