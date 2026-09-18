package cn.hamm.spms.module.channel.customer;

import cn.hamm.spms.core.CollectionUtil;
import cn.hamm.spms.framework.curd.helper.ExportHelper;
import cn.hamm.spms.framework.curd.model.query.Page;
import cn.hamm.spms.framework.curd.model.query.QueryListRequest;
import cn.hamm.spms.framework.curd.model.query.QueryPageRequest;
import cn.hamm.spms.framework.curd.model.query.QueryPageResponse;
import cn.hamm.spms.framework.curd.model.query.Sort;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.common.CsvFormulaSanitizer;
import cn.hamm.spms.module.channel.customer.mapper.CustomerMapper;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
public class CustomerService extends BaseService<CustomerEntity, CustomerRepository> {
    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

    /**
     * 读侧开关关闭时 Mapper Bean 根本不存在；这里必须是可选注入，才能保留「关掉读侧就回到引擎读路径」的回滚位。
     */
    @Autowired(required = false)
    private CustomerMapper customerMapper;

    @Autowired
    private ExportHelper exportHelper;

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    /**
     * @return 是否已启用客户 Mapper 读路径
     */
    public boolean isMapperReadEnabled() {
        return customerMapper != null;
    }

    /**
     * 客户分页读出口；写路径走写侧仓储（`ICurdRepository` + mapper XML）。
     */
    public @NotNull QueryPageResponse<CustomerEntity> getMapperPage(QueryPageRequest<CustomerEntity> request) {
        QueryPageRequest<CustomerEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        CustomerEntity filter = normalized.getFilter() == null ? new CustomerEntity() : normalized.getFilter();
        Page requestedPage = normalized.getPage() == null ? new Page() : normalized.getPage();
        int pageNum = requestedPage.getPageNum() == null ? 1 : requestedPage.getPageNum();
        int pageSize = requestedPage.getPageSize() == null
                ? DEFAULT_READ_PAGE_SIZE : requestedPage.getPageSize();
        if (pageNum < 1 || pageSize < 1) {
            throw new IllegalArgumentException("分页页码和分页大小必须为正数");
        }
        pageSize = Math.min(pageSize, MAX_READ_PAGE_SIZE);
        long offsetValue = ((long) pageNum - 1L) * pageSize;
        if (offsetValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("分页页码超出可查询范围");
        }
        int offset = (int) offsetValue;
        List<CustomerEntity> list = customerMapper.selectPageByPermission(filter, offset, pageSize);
        long total = customerMapper.countByPermission(filter);
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        Sort sort = normalized.getSort() == null ? new Sort() : normalized.getSort();
        QueryPageResponse<CustomerEntity> response = new QueryPageResponse<>();
        response.setList(list);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(sort);
        return response;
    }

    /**
     * 客户详情读出口；无权行与不存在行都由 Mapper 返回 null。
     *
     * @param id 主键
     * @return 详情或 null
     */
    public CustomerEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        return customerMapper.selectDetailByPermission(id);
    }

    /**
     * 客户总数读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内行数
     */
    public long getMapperCount(CustomerEntity filter) {
        CustomerEntity normalized = filter == null ? new CustomerEntity() : filter;
        if (isMapperReadEnabled()) {
            return customerMapper.countByPermission(normalized);
        }
        return filter(normalized).size();
    }

    /**
     * 客户统计读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内统计
     */
    public ReadStat getMapperStat(CustomerEntity filter) {
        CustomerEntity normalized = filter == null ? new CustomerEntity() : filter;
        if (isMapperReadEnabled()) {
            return customerMapper.statByPermission(normalized);
        }
        List<CustomerEntity> rows = filter(normalized);
        ReadStat stat = new ReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    /**
     * 客户真实导出出口（Mapper）。
     *
     * <p>行集经 {@link CustomerMapper#selectForExport} 走同一条权限拦截链，列级遮蔽在<b>请求线程</b>
     * 完成后交给 {@link ExportHelper#createExportTask} 落盘，保留异步下载契约。</p>
     *
     * @param request 导出请求（含业务筛选）
     * @return 导出任务文件编码，供前端 {@code queryExport} 轮询下载
     */
    public String createMapperExportTask(QueryListRequest<CustomerEntity> request) {
        CustomerEntity filter = request == null || request.getFilter() == null
                ? new CustomerEntity() : request.getFilter();
        List<CustomerEntity> rows = customerMapper.selectForExport(filter);
        List<CustomerEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = CollectionUtil.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), CustomerEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    @Override
    protected void addSearchPredicate(@NotNull QueryWrapper<CustomerEntity> wrapper, @NotNull CustomerEntity search) {
        // 数据权限：渲染器产出的 SQL 片段直接挂到 wrapper 上（J2 起不再编 JPA Criteria）。
        // 引擎把这个钩子接在通用条件之后，所以片段与用户条件天然是 AND 关系。
        dataPermissionSpecification.applyQuery(wrapper, getEntityClass(), "channel.customer", DataPermissionAction.QUERY);
    }
}
