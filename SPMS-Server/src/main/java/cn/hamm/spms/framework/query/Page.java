package cn.hamm.spms.framework.query;

import java.io.Serializable;
import java.util.Objects;

/**
 * <h1>自持分页参数</h1>
 *
 * <p>纯 JDK 17 实现，无第三方注解或框架依赖，与前端分页请求格式完全兼容。</p>
 *
 * @author Hamm.cn
 */
public class Page implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;

    private Integer pageNum = DEFAULT_PAGE_NUM;
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    public Page() {
    }

    public Page(Integer pageNum, Integer pageSize) {
        this.pageNum = pageNum != null ? pageNum : DEFAULT_PAGE_NUM;
        this.pageSize = pageSize != null ? pageSize : DEFAULT_PAGE_SIZE;
    }

    public Integer getPageNum() {
        return pageNum;
    }

    public Page setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
        return this;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public Page setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Page page = (Page) o;
        return Objects.equals(pageNum, page.pageNum) && Objects.equals(pageSize, page.pageSize);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pageNum, pageSize);
    }

    @Override
    public String toString() {
        return "Page{" +
                "pageNum=" + pageNum +
                ", pageSize=" + pageSize +
                '}';
    }
}
