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

    /**
     * 业务实体筛选条件。
     *
     * <p><b>可以为 null</b>：无参构造不会初始化它，前端只传分页/排序、或清空筛选后提交
     * {@code {}} 时都是 null。<b>覆写 {@code BaseService.beforeGetList} /
     * {@code beforeGetPage} / {@code beforeCreatePredicate} 时必须先判空再解引用</b>，
     * 否则一次不带筛选的查询就是未捕获 NPE → HTTP 500。</p>
     *
     * <p>{@code BaseService.getList} / {@code getPage} 已在调用钩子<b>之前</b>把 null 归一化成
     * 空实体实例（与 {@code createSpecification} 下游既有的替换语义一致，不改变查询结果），
     * 因此走标准读链路的钩子拿到的 filter 一定非 null；但直接构造本对象再调钩子的路径
     * （如内部复用、单元测试）不受该保护，仍需自行判空。</p>
     */
    private T filter;

    public QueryRequest() {
    }

    public QueryRequest(T filter) {
        this.filter = filter;
    }

    /**
     * @return 筛选条件；<b>可能为 null</b>，见字段说明
     */
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
