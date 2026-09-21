package cn.hamm.spms.framework.util;

import cn.hamm.spms.framework.query.Page;
import cn.hamm.spms.framework.query.QueryListRequest;
import cn.hamm.spms.framework.query.QueryPageRequest;
import cn.hamm.spms.framework.query.QueryPageResponse;
import cn.hamm.spms.framework.query.QueryRequest;
import cn.hamm.spms.framework.query.ReadPage;
import cn.hamm.spms.framework.query.Sort;

import java.util.List;
import java.util.Objects;

import static cn.hamm.spms.framework.util.UtilityContracts.check;
import static cn.hamm.spms.framework.util.UtilityContracts.equal;

/**
 * <h1>自持查询模型（Request/Response/Page/Sort）可执行契约</h1>
 *
 * <p>验证自持查询模型的基础属性、默认值、流式调用、不可变/空安全规约与工厂方法，
 * 保证与前端/协议层的字段命名和行为完全契合。</p>
 *
 * @author Hamm.cn
 */
final class QueryModelContracts {

    private QueryModelContracts() {
    }

    static List<UtilityContracts.Case> cases() {
        return List.of(
                new UtilityContracts.Case("query-models/page-defaults-and-chaining", () -> {
                    Page page = new Page();
                    equal(1, page.getPageNum());
                    equal(20, page.getPageSize());

                    Page custom = new Page(3, 50);
                    equal(3, custom.getPageNum());
                    equal(50, custom.getPageSize());

                    Page chained = new Page().setPageNum(5).setPageSize(100);
                    equal(5, chained.getPageNum());
                    equal(100, chained.getPageSize());

                    Page nullSafe = new Page(null, null);
                    equal(1, nullSafe.getPageNum());
                    equal(20, nullSafe.getPageSize());
                }),

                new UtilityContracts.Case("query-models/sort-directions-and-flags", () -> {
                    Sort sort = new Sort();
                    check("asc".equalsIgnoreCase(sort.getDirection()));
                    check(sort.isAsc());
                    check(!sort.isDesc());

                    Sort desc = new Sort("createTime", Sort.DESC);
                    equal("createTime", desc.getField());
                    equal("desc", desc.getDirection());
                    check(!desc.isAsc());
                    check(desc.isDesc());

                    Sort uppercase = new Sort("id", "DESC");
                    check(uppercase.isDesc());
                    check(!uppercase.isAsc());
                }),

                new UtilityContracts.Case("query-models/query-request-filter-binding", () -> {
                    QueryRequest<String> request = new QueryRequest<>();
                    check(request.getFilter() == null);
                    request.setFilter("sample-filter");
                    equal("sample-filter", request.getFilter());
                }),

                new UtilityContracts.Case("query-models/query-list-request-sort-and-filter", () -> {
                    QueryListRequest<String> req = new QueryListRequest<>("f1", new Sort("name", "asc"));
                    equal("f1", req.getFilter());
                    check(req.getSort() != null);
                    equal("name", req.getSort().getField());

                    QueryListRequest<String> copy = new QueryListRequest<String>("f1").setSort(new Sort("name", "asc"));
                    equal(req, copy);
                    equal(req.hashCode(), copy.hashCode());
                }),

                new UtilityContracts.Case("query-models/query-page-request-page-default-guard", () -> {
                    QueryPageRequest<String> req = new QueryPageRequest<>();
                    check(req.getPage() != null);
                    equal(1, req.getPage().getPageNum());

                    req.setPage(null);
                    check(req.getPage() != null);

                    QueryPageRequest<String> full = new QueryPageRequest<>("filter", new Page(2, 30), new Sort("code", "desc"));
                    equal("filter", full.getFilter());
                    equal(2, full.getPage().getPageNum());
                    equal(30, full.getPage().getPageSize());
                    equal("code", full.getSort().getField());
                }),

                new UtilityContracts.Case("query-models/query-page-response-factory-of", () -> {
                    ReadPage paging = ReadPage.from(2, 20);
                    List<String> rows = List.of("a", "b", "c");
                    Sort sort = new Sort("id", "asc");

                    QueryPageResponse<String> response = QueryPageResponse.of(paging, rows, 45, sort);
                    equal(45, response.getTotal());
                    equal(3, response.getPageCount());
                    equal(2, response.getPage().getPageNum());
                    equal(20, response.getPage().getPageSize());
                    equal(3, response.getList().size());
                    check(response.getSort() != null);
                    equal("id", response.getSort().getField());
                }),

                new UtilityContracts.Case("query-models/query-page-response-empty-and-null-list", () -> {
                    QueryPageResponse<Object> empty = new QueryPageResponse<>();
                    check(empty.getList() != null);
                    check(empty.getList().isEmpty());
                    empty.setList(null);
                    check(empty.getList() != null);
                })
        );
    }
}
