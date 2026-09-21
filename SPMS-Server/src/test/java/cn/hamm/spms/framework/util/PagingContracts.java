package cn.hamm.spms.framework.util;

import cn.hamm.spms.framework.query.ReadPage;

import java.util.List;
import java.util.Random;

import static cn.hamm.spms.framework.util.UtilityContracts.check;
import static cn.hamm.spms.framework.util.UtilityContracts.equal;
import static cn.hamm.spms.framework.util.UtilityContracts.fails;

/**
 * <h1>读侧分页计算的独立契约</h1>
 *
 * <p>固定旧服务算法的默认值、钳制顺序、异常文案、int 响应范围。
 * 不创建假的 Page/Sort/实体，不冒充旧 DTO 的 Maven 接线测试。</p>
 *
 * @author Hamm.cn
 */
final class PagingContracts {
    private PagingContracts() {
    }

    static List<UtilityContracts.Case> cases() {
        return List.of(
                new UtilityContracts.Case("paging/defaults", () -> {
                    ReadPage page = ReadPage.from(null, null);
                    equal(1, page.pageNum());
                    equal(20, page.pageSize());
                    equal(0, page.offset());
                }),
                new UtilityContracts.Case("paging/partial-defaults", () -> {
                    equal(new ReadPage(1, 5), ReadPage.from(null, 5));
                    equal(new ReadPage(3, 20), ReadPage.from(3, null));
                }),
                new UtilityContracts.Case("paging/offset-uses-one-based-pages", () -> {
                    equal(150, ReadPage.from(4, 50).offset());
                    equal(0, ReadPage.from(1, 200).offset());
                }),
                new UtilityContracts.Case("paging/size-cap-before-offset", () -> {
                    ReadPage page = ReadPage.from(2, Integer.MAX_VALUE);
                    equal(200, page.pageSize());
                    equal(200, page.offset());
                    equal(new ReadPage(2, 200), page);
                }),
                new UtilityContracts.Case("paging/nonpositive-number-and-message", () -> {
                    for (int number : new int[]{0, -1, Integer.MIN_VALUE}) {
                        IllegalArgumentException error = fails(IllegalArgumentException.class, () -> ReadPage.from(number, 20));
                        equal("分页页码和分页大小必须为正数", error.getMessage());
                    }
                }),
                new UtilityContracts.Case("paging/nonpositive-size-and-message", () -> {
                    for (int size : new int[]{0, -1, Integer.MIN_VALUE}) {
                        IllegalArgumentException error = fails(IllegalArgumentException.class, () -> ReadPage.from(1, size));
                        equal("分页页码和分页大小必须为正数", error.getMessage());
                    }
                }),
                new UtilityContracts.Case("paging/largest-valid-offsets", () -> {
                    equal(Integer.MAX_VALUE - 1, ReadPage.from(Integer.MAX_VALUE, 1).offset());
                    equal(2147483600, ReadPage.from(10737419, 200).offset());
                }),
                new UtilityContracts.Case("paging/overflow-does-not-wrap-to-negative", () -> {
                    for (int size : new int[]{2, 20, 200, Integer.MAX_VALUE}) {
                        IllegalArgumentException error = fails(IllegalArgumentException.class, () -> ReadPage.from(Integer.MAX_VALUE, size));
                        equal("分页页码超出可查询范围", error.getMessage());
                    }
                    fails(IllegalArgumentException.class, () -> ReadPage.from(10737420, 200));
                }),
                new UtilityContracts.Case("paging/empty-count", () -> equal(new ReadPage.Counts(0, 0), ReadPage.from(1, 20).counts(0))),
                new UtilityContracts.Case("paging/exact-and-partial-final-page", () -> {
                    ReadPage page = ReadPage.from(1, 20);
                    equal(new ReadPage.Counts(1, 1), page.counts(1));
                    equal(new ReadPage.Counts(40, 2), page.counts(40));
                    equal(new ReadPage.Counts(41, 3), page.counts(41));
                }),
                new UtilityContracts.Case("paging/count-after-page-size-cap", () -> {
                    equal(new ReadPage.Counts(401, 3), ReadPage.from(1, 1000).counts(401));
                }),
                new UtilityContracts.Case("paging/maximum-int-total", () -> {
                    equal(new ReadPage.Counts(Integer.MAX_VALUE, 10737419), ReadPage.from(1, 200).counts(Integer.MAX_VALUE));
                }),
                new UtilityContracts.Case("paging/total-overflow-not-truncated", () -> {
                    for (long total : new long[]{Integer.MAX_VALUE + 1L, Long.MAX_VALUE, Integer.MIN_VALUE - 1L, Long.MIN_VALUE}) {
                        fails(ArithmeticException.class, () -> ReadPage.from(1, 200).counts(total));
                    }
                }),
                new UtilityContracts.Case("paging/legacy-negative-count-policy-not-silently-redefined", () -> {
                    equal(new ReadPage.Counts(-1, 0), ReadPage.from(1, 20).counts(-1));
                }),
                new UtilityContracts.Case("paging/independent-values-do-not-share-state", () -> {
                    ReadPage first = ReadPage.from(4, 50);
                    ReadPage.from(1, Integer.MAX_VALUE).counts(500);
                    equal(4, first.pageNum());
                    equal(50, first.pageSize());
                    equal(150, first.offset());
                    check(ReadPage.class.isRecord());
                }),
                new UtilityContracts.Case("paging/generated-differential", PagingContracts::generatedParity)
        );
    }

    private static void generatedParity() throws Exception {
        Random random = new Random(20260921L);
        for (int i = 0; i < 5000; i++) {
            Integer number = i % 11 == 0 ? null : random.nextInt(Integer.MAX_VALUE);
            Integer size = i % 13 == 0 ? null : random.nextInt(5000) - 5;
            int[] expected;
            try {
                expected = oldCalculation(number, size);
            } catch (IllegalArgumentException expectedError) {
                IllegalArgumentException actualError = fails(IllegalArgumentException.class, () -> ReadPage.from(number, size));
                equal(expectedError.getMessage(), actualError.getMessage());
                continue;
            }
            ReadPage actual = ReadPage.from(number, size);
            equal(expected[0], actual.pageNum());
            equal(expected[1], actual.pageSize());
            equal(expected[2], actual.offset());
            long total = random.nextInt(Integer.MAX_VALUE);
            equal(new ReadPage.Counts(Math.toIntExact(total), total == 0 ? 0 : Math.toIntExact((total + expected[1] - 1L) / expected[1])), actual.counts(total));
        }
    }

    /** 原 14 个业务方法共同的数值分支，仅作为差分参考；无任何第三方类 stub。 */
    private static int[] oldCalculation(Integer requestedNumber, Integer requestedSize) {
        int pageNum = requestedNumber == null ? 1 : requestedNumber;
        int pageSize = requestedSize == null ? 20 : requestedSize;
        if (pageNum < 1 || pageSize < 1) {
            throw new IllegalArgumentException("分页页码和分页大小必须为正数");
        }
        pageSize = Math.min(pageSize, 200);
        long offset = ((long) pageNum - 1L) * pageSize;
        if (offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("分页页码超出可查询范围");
        }
        return new int[]{pageNum, pageSize, (int) offset};
    }
}
