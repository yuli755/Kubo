package cn.hamm.spms.framework.query;

import java.io.Serializable;
import java.util.Objects;

/**
 * <h1>自持排序参数</h1>
 *
 * <p>纯 JDK 17 实现，无第三方注解或框架依赖，与前端排序请求格式完全兼容。</p>
 *
 * @author Hamm.cn
 */
public class Sort implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String ASC = "asc";
    public static final String DESC = "desc";

    private String field;
    private String direction = ASC;

    public Sort() {
    }

    public Sort(String field, String direction) {
        this.field = field;
        this.direction = direction != null ? direction : ASC;
    }

    public String getField() {
        return field;
    }

    public Sort setField(String field) {
        this.field = field;
        return this;
    }

    public String getDirection() {
        return direction;
    }

    public Sort setDirection(String direction) {
        this.direction = direction;
        return this;
    }

    public boolean isAsc() {
        return ASC.equalsIgnoreCase(direction);
    }

    public boolean isDesc() {
        return DESC.equalsIgnoreCase(direction);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sort sort = (Sort) o;
        return Objects.equals(field, sort.field) && Objects.equals(direction, sort.direction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, direction);
    }

    @Override
    public String toString() {
        return "Sort{" +
                "field='" + field + '\'' +
                ", direction='" + direction + '\'' +
                '}';
    }
}
