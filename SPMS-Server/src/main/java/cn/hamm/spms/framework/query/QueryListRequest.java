package cn.hamm.spms.framework.query;

import java.util.Objects;

/**
 * <h1>自持列表查询请求模型</h1>
 *
 * <p>纯 JDK 17 实现，继承 {@link QueryRequest}，补充可选的排序参数。</p>
 *
 * @param <T> 实体类型
 * @author Hamm.cn
 */
public class QueryListRequest<T> extends QueryRequest<T> {
    private static final long serialVersionUID = 1L;

    private Sort sort;

    public QueryListRequest() {
    }

    public QueryListRequest(T filter) {
        super(filter);
    }

    public QueryListRequest(T filter, Sort sort) {
        super(filter);
        this.sort = sort;
    }

    public Sort getSort() {
        return sort;
    }

    public QueryListRequest<T> setSort(Sort sort) {
        this.sort = sort;
        return this;
    }

    @Override
    public QueryListRequest<T> setFilter(T filter) {
        super.setFilter(filter);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        QueryListRequest<?> that = (QueryListRequest<?>) o;
        return Objects.equals(sort, that.sort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sort);
    }

    @Override
    public String toString() {
        return "QueryListRequest{" +
                "filter=" + getFilter() +
                ", sort=" + sort +
                '}';
    }
}
