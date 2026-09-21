package cn.hamm.spms.framework.query;

/**
 * <h1>Mapper 读侧的分页计算，不依赖请求 DTO、ORM 或框架</h1>
 *
 * <p>只归并原有算法：默认 1/20，每页最多 200，偏移不得超出 int。
 * 不决定筛选、排序、权限、SQL 或 JPA 回退路径。</p>
 *
 * @author Hamm.cn
 */
public record ReadPage(int pageNum, int pageSize) {
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 200;

    public ReadPage {
        if (pageNum < 1 || pageSize < 1) {
            throw new IllegalArgumentException("分页页码和分页大小必须为正数");
        }
        pageSize = Math.min(pageSize, MAX_PAGE_SIZE);
        if (((long) pageNum - 1L) * pageSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("分页页码超出可查询范围");
        }
    }

    /** 字段缺省值保持现有读侧契约，不改写调用方请求对象。 */
    public static ReadPage from(Integer pageNum, Integer pageSize) {
        return new ReadPage(pageNum == null ? 1 : pageNum, pageSize == null ? DEFAULT_PAGE_SIZE : pageSize);
    }

    /** 使用已经钳制、校验过的 pageSize 计算 SQL 偏移，不发生 int 乘法溢出。 */
    public int offset() {
        return (pageNum - 1) * pageSize;
    }

    /**
     * 现有响应的 total/pageCount 都是 int，先校验 total 的可表示范围，再计算页数。
     * COUNT 正常为非负；本批不为异常 DAO 返回值新增与旧接口不同的错误策略。
     */
    public Counts counts(long total) {
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        return new Counts(totalAsInt, pageCount);
    }

    /** 只表达统计结果；没有 JSON/ORM 类型或可变共享状态。 */
    public record Counts(int total, int pageCount) {
    }
}
