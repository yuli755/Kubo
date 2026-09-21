package cn.hamm.spms.framework.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * <h1>B2a 工具契约：同一组断言同时供独立 Java 17 runner 与 JUnit 执行</h1>
 *
 * <p>没有 Spring、Lombok、JUnit 或框架依赖。独立 runner 只证明这些生产工具可编译、可运行，
 * 不冒充整个应用的 Maven 编译和集成测试。</p>
 *
 * @author Hamm.cn
 */
public final class UtilityContracts {
    @FunctionalInterface
    public interface Check {
        void run() throws Exception;
    }

    public record Case(String name, Check check) {
    }

    private UtilityContracts() {
    }

    /** JUnit 与离线 runner 的唯一用例清单。 */
    public static List<Case> cases() {
        List<Case> cases = new ArrayList<>(List.of(
                new Case("decimal/add", () -> equal(0.3, DecimalMath.add(0.1, 0.2))),
                new Case("decimal/subtract", () -> equal(0.2, DecimalMath.subtract(0.3, 0.1))),
                new Case("decimal/multiply", () -> equal(0.02, DecimalMath.multiply(0.1, 0.2))),
                new Case("decimal/sign-and-no-rounding", () -> {
                    equal(-0.03, DecimalMath.multiply(-0.1, 0.3));
                    equal(1.2345678901, DecimalMath.add(1.2345678901, 0));
                    equal(0.0, DecimalMath.subtract(-0.1, -0.1));
                }),
                new Case("decimal/non-finite-rejected", () -> {
                    fails(NumberFormatException.class, () -> DecimalMath.add(Double.NaN, 1));
                    fails(NumberFormatException.class, () -> DecimalMath.subtract(1, Double.POSITIVE_INFINITY));
                    fails(NumberFormatException.class, () -> DecimalMath.multiply(Double.NEGATIVE_INFINITY, 1));
                }),
                new Case("random/default-cookie-shape", () -> matches("[A-Za-z0-9]{32}", RandomValues.randomString())),
                new Case("random/salt-lengths", () -> {
                    for (int length : new int[]{1, 4, 16, 32, 128}) {
                        matches("[A-Za-z0-9]{" + length + "}", RandomValues.randomString(length));
                    }
                }),
                new Case("random/verification-code-shape", () -> {
                    for (int i = 0; i < 100; i++) {
                        matches("[0-9]{6}", RandomValues.randomNumbers(6));
                    }
                }),
                new Case("random/non-positive-length-compatibility", () -> {
                    equal(1, RandomValues.randomString(0).length());
                    equal(1, RandomValues.randomString(-4).length());
                    matches("[0-9]", RandomValues.randomNumbers(0));
                }),
                new Case("random/concurrent-callers", UtilityContracts::randomConcurrent),
                new Case("file/extensions", () -> {
                    equal("jpg", FilePaths.getExtension("照片.JPG"));
                    equal("gz", FilePaths.getExtension("archive.tar.GZ"));
                    equal("", FilePaths.getExtension("name."));
                    equal("", FilePaths.getExtension(""));
                    equal("readme", FilePaths.getExtension("README"));
                    equal("hidden", FilePaths.getExtension(".hidden"));
                }),
                new Case("file/locale-independent-extension", () -> {
                    Locale previous = Locale.getDefault();
                    try {
                        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                        equal("gif", FilePaths.getExtension("IMAGE.GIF"));
                    } finally {
                        Locale.setDefault(previous);
                    }
                }),
                new Case("file/directory-compatibility", () -> {
                    equal("upload" + File.separator, FilePaths.formatDirectory("upload"));
                    equal("upload" + File.separator, FilePaths.formatDirectory("upload" + File.separator));
                    equal(File.separator, FilePaths.formatDirectory(""));
                    equal(1024L, FilePaths.FILE_SCALE);
                    fails(NullPointerException.class, () -> FilePaths.formatDirectory(null));
                }),
                new Case("file/today-directory", () -> {
                    LocalDate before = LocalDate.now();
                    String actual = FilePaths.getTodayDirectory();
                    LocalDate after = LocalDate.now();
                    check(actual.equals(before.format(DateTimeFormatter.BASIC_ISO_DATE) + File.separator)
                            || actual.equals(after.format(DateTimeFormatter.BASIC_ISO_DATE) + File.separator));
                }),
                new Case("file/create-and-truncate", UtilityContracts::fileCreateAndTruncate),
                new Case("file/io-failure-propagates", UtilityContracts::fileFailures),
                new Case("enum/lookup-without-legacy-interface", () -> {
                    equal(Option.FIRST, EnumValues.find(Option.class, 10, Option::key).orElseThrow());
                    equal(Option.SECOND, EnumValues.find(Option.class, -2, Option::key).orElseThrow());
                    check(EnumValues.find(Option.class, 999, Option::key).isEmpty());
                }),
                new Case("enum/duplicate-key-first-wins", () ->
                        equal(Option.FIRST, EnumValues.find(Option.class, 1, ignored -> 1).orElseThrow())),
                new Case("enum/explicit-wire-fields-and-order", UtilityContracts::enumOptions),
                new Case("enum/null-value-is-not-dropped", () -> {
                    var row = EnumValues.options(Option.class, EnumValues.column("optional", ignored -> null)).get(0);
                    check(row.containsKey("optional"));
                    equal(null, row.get("optional"));
                }),
                new Case("enum/empty-enum", () -> {
                    equal(List.of(), EnumValues.options(EmptyOption.class));
                    check(EnumValues.find(EmptyOption.class, 1, ignored -> 1).isEmpty());
                }),
                new Case("enum/invalid-column-rejected", () -> {
                    fails(NullPointerException.class, () -> EnumValues.column(null, Option::key));
                    fails(NullPointerException.class, () -> EnumValues.column("key", null));
                }),
                new Case("tree/empty-tree", () -> equal(List.of(), Trees.buildTreeList(List.<Node>of()))),
                new Case("tree/roots-order-orphans-and-identity", UtilityContracts::treeShape),
                new Case("tree/immutable-results-and-fresh-leaves", UtilityContracts::treeImmutable),
                new Case("tree/duplicate-or-root-id-rejected", () -> {
                    fails(IllegalArgumentException.class, () -> Trees.buildTreeList(List.of(node(1, 0), node(1, 0))));
                    fails(IllegalArgumentException.class, () -> Trees.buildTreeList(List.of(node(0, 0))));
                    fails(NullPointerException.class, () -> Trees.buildTreeList(List.of(new Node(null, 0L))));
                }),
                new Case("tree/disconnected-cycle-is-not-promoted", () -> {
                    Node root = node(1, 0);
                    equal(List.of(root), Trees.buildTreeList(List.of(root, node(2, 3), node(3, 2))));
                }),
                new Case("tree/deep-build-without-stack-overflow", UtilityContracts::treeDeepBuild),
                new Case("tree/descendants-null-and-mutable-result", () -> {
                    Set<Long> ids = Trees.<Node>getChildrenIdList(8, ignored -> null);
                    equal(Set.of(), ids);
                    ids.add(8L);
                    equal(Set.of(8L), ids);
                }),
                new Case("tree/descendants-cycle-and-duplicate-termination", UtilityContracts::treeCycles),
                new Case("tree/deep-descendants-without-stack-overflow", UtilityContracts::treeDeepDescendants),
                new Case("tree/delete-probe-null-empty-and-child", () -> {
                    check(!Trees.hasChildren(1, ignored -> null));
                    check(!Trees.hasChildren(1, ignored -> List.of()));
                    check(Trees.hasChildren(1, id -> List.of(node(2, id))));
                })
        ));
        cases.addAll(ExportContracts.cases());
        cases.addAll(DomainEnumContracts.cases());
        cases.addAll(PhoneContracts.cases());
        cases.addAll(PagingContracts.cases());
        cases.addAll(QueryModelContracts.cases());
        cases.addAll(ErrorContracts.cases());
        cases.addAll(PasswordContracts.cases());
        cases.addAll(CoreActionContracts.cases());
        return List.copyOf(cases);
    }

    /** 不启用 -ea 也必须执行断言，不能因 JVM 参数遗漏而空转。 */
    public static void main(String[] args) throws Exception {
        int passed = 0;
        for (Case test : cases()) {
            test.check().run();
            System.out.println("PASS " + test.name());
            passed++;
        }
        if (passed == 0) {
            throw new AssertionError("没有执行工具契约");
        }
        System.out.println("Utility contracts: " + passed + " passed (independent subset, not Maven integration tests)");
    }

    private static void randomConcurrent() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<java.util.concurrent.Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                tasks.add(RandomValues::randomString);
            }
            for (var result : pool.invokeAll(tasks)) {
                matches("[A-Za-z0-9]{32}", result.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void fileCreateAndTruncate() throws Exception {
        Path temp = Files.createTempDirectory("kubo-upload-contract-");
        try {
            Path directory = temp.resolve("nested/day");
            FilePaths.saveFile(directory.toString(), "hash.txt", "longer content".getBytes(StandardCharsets.UTF_8));
            equal("longer content", Files.readString(directory.resolve("hash.txt")));
            FilePaths.saveFile(directory.toString(), "hash.txt", "短".getBytes(StandardCharsets.UTF_8));
            equal("短", Files.readString(directory.resolve("hash.txt")));
            FilePaths.saveFile(directory.toString(), "hash.txt", new byte[0]);
            equal(0L, Files.size(directory.resolve("hash.txt")));
        } finally {
            delete(temp);
        }
    }

    private static void fileFailures() throws Exception {
        Path temp = Files.createTempDirectory("kubo-upload-failure-");
        try {
            Path notDirectory = Files.writeString(temp.resolve("file"), "x");
            IOException createError = fails(IOException.class, () ->
                    FilePaths.saveFile(notDirectory.resolve("child").toString(), "hash.txt", new byte[0]));
            check(createError.getMessage().startsWith("自动创建文件夹失败，"));
            check(createError.getCause() instanceof IOException);
            IOException saveError = fails(IOException.class, () ->
                    FilePaths.saveFile(temp.toString(), "file/child", new byte[0]));
            check(saveError.getMessage().startsWith("文件保存失败，"));
            check(saveError.getCause() instanceof IOException);
        } finally {
            delete(temp);
        }
    }

    private static void enumOptions() {
        var rows = EnumValues.options(Option.class,
                EnumValues.column("key", Option::key),
                EnumValues.column("label", Option::label),
                EnumValues.column("desc", value -> "desc-" + value.label()));
        equal(List.of(Map.of("key", 10, "label", "第一项", "desc", "desc-第一项"),
                Map.of("key", -2, "label", "第二项", "desc", "desc-第二项")), rows);
        equal(List.of("key", "label", "desc"), new ArrayList<>(rows.get(0).keySet()));
        rows.get(0).put("key", 999);
        equal(10, EnumValues.options(Option.class, EnumValues.column("key", Option::key)).get(0).get("key"));
    }

    private static void treeShape() {
        Node root2 = node(2, 0);
        Node root1 = new Node(1L, null);
        Node child4 = node(4, 1);
        Node child3 = node(3, 1);
        Node orphan = node(9, 99);
        List<Node> roots = Trees.buildTreeList(List.of(child4, root2, orphan, root1, child3));
        equal(List.of(root2, root1), roots);
        equal(List.of(child4, child3), root1.children);
        check(roots.get(1) == root1);
        check(root1.children.get(0) == child4);
        equal(null, orphan.children);
    }

    private static void treeImmutable() throws Exception {
        Node root = node(1, 0);
        Node leaf = node(2, 1);
        leaf.children = List.of(node(77, 2));
        List<Node> roots = Trees.buildTreeList(List.of(root, leaf));
        equal(List.of(), leaf.children);
        fails(UnsupportedOperationException.class, () -> roots.add(node(4, 0)));
        fails(UnsupportedOperationException.class, () -> root.children.clear());
        fails(UnsupportedOperationException.class, () -> leaf.children.add(node(4, 2)));
    }

    private static void treeDeepBuild() {
        List<Node> nodes = new ArrayList<>();
        for (long i = 1; i <= 20000; i++) {
            nodes.add(node(i, i - 1));
        }
        Node current = Trees.buildTreeList(nodes).get(0);
        int depth = 1;
        while (!current.children.isEmpty()) {
            current = current.children.get(0);
            depth++;
        }
        equal(20000, depth);
    }

    private static void treeCycles() {
        Map<Long, Integer> calls = new HashMap<>();
        Set<Long> ids = Trees.getChildrenIdList(1, id -> {
            calls.merge(id, 1, Integer::sum);
            if (id == 1L) {
                return List.of(node(2, 1));
            }
            if (id == 2L) {
                return List.of(node(1, 2), node(3, 2), node(3, 2));
            }
            return null;
        });
        equal(Set.of(2L, 3L), ids);
        equal(Map.of(1L, 1, 2L, 1, 3L, 1), calls);
    }

    private static void treeDeepDescendants() {
        Set<Long> ids = Trees.getChildrenIdList(1, id -> id < 20000 ? List.of(node(id + 1, id)) : List.of());
        equal(19999, ids.size());
        check(ids.contains(20000L));
        check(!ids.contains(1L));
    }

    private static Node node(long id, long parent) {
        return new Node(id, parent);
    }

    private static void delete(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("expected=" + expected + ", actual=" + actual);
        }
    }

    static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("contract violated");
        }
    }

    static void matches(String pattern, String actual) {
        if (!actual.matches(pattern)) {
            throw new AssertionError("value does not match " + pattern);
        }
    }

    static <T extends Throwable> T fails(Class<T> type, Check operation) throws Exception {
        try {
            operation.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) {
                return type.cast(error);
            }
            throw error;
        }
        throw new AssertionError("expected " + type.getName());
    }

    // 普通枚举/POJO 刻意不实现第三方接口，避免靠遗留类型才测得通的假解耦。
    private enum Option {
        FIRST(10, "第一项"), SECOND(-2, "第二项");

        private final int key;
        private final String label;

        Option(int key, String label) {
            this.key = key;
            this.label = label;
        }

        int key() {
            return key;
        }

        String label() {
            return label;
        }
    }

    private enum EmptyOption {
        ;
    }

    private static final class Node implements TreeNode<Node> {
        private final Long id;
        private final Long parentId;
        private List<Node> children;

        Node(Long id, Long parentId) {
            this.id = id;
            this.parentId = parentId;
        }

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public Long getParentId() {
            return parentId;
        }

        @Override
        public Node setChildren(List<Node> children) {
            this.children = children;
            return this;
        }
    }
}
