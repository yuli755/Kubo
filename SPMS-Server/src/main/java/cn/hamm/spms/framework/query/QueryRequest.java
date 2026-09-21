package cn.hamm.spms.framework.query;

import java.io.Serializable;
import java.util.Objects;

/**
 * <h1>自持查询请求基础模型</h1>
 *
 * <p>纯 JDK 17 实现，无第三方注解或框架依赖，承载业务实体筛选条件。</p>
 *
 * @param <T> 实体类型
 * @author Hamm.cn
 */
public class QueryRequest<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private T filter;

    public QueryRequest() {
    }

    public QueryRequest(T filter) {
        this.filter = filter;
    }

    public T getFilter() {
        return filter;
    }

    public QueryRequest<T> setFilter(T filter) {
        this.filter = filter;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueryRequest<?> that = (QueryRequest<?>) o;
        return Objects.equals(filter, that.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filter);
    }

    @Override
    public String toString() {
        return "QueryRequest{" +
                "filter=" + filter +
                '}';
    }
}
