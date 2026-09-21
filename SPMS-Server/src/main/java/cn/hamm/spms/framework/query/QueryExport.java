package cn.hamm.spms.framework.query;

import java.io.Serializable;

/**
 * <h1>异步导出查询参数模型</h1>
 *
 * <p>自持异步导出结果查询 DTO，纯 JDK 实现，替代旧 {@code cn.hamm.airpower.curd.model.query.QueryExport}。</p>
 *
 * @author Hamm.cn
 */
public class QueryExport implements Serializable {
    private static final long serialVersionUID = 1L;

    private String fileCode;

    public QueryExport() {
    }

    public QueryExport(String fileCode) {
        this.fileCode = fileCode;
    }

    public String getFileCode() {
        return fileCode;
    }

    public QueryExport setFileCode(String fileCode) {
        this.fileCode = fileCode;
        return this;
    }
}
