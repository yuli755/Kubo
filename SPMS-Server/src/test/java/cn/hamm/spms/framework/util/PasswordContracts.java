package cn.hamm.spms.framework.util;

import cn.hamm.spms.framework.error.BusinessException;
import cn.hamm.spms.framework.security.PasswordHasher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static cn.hamm.spms.framework.util.UtilityContracts.check;
import static cn.hamm.spms.framework.util.UtilityContracts.equal;
import static cn.hamm.spms.framework.util.UtilityContracts.fails;

/**
 * <h1>密码加盐散列的 JDK-only 契约</h1>
 *
 * @author Hamm.cn
 */
final class PasswordContracts {
    private PasswordContracts() {
    }

    static List<UtilityContracts.Case> cases() {
        return List.of(
                new UtilityContracts.Case("password/golden-hash-vectors", () -> {
                    equal("1b7849727afe7bc11be3562248b9688f4c346e02", PasswordHasher.encode("Aa123456", "salt"));
                    equal("914f46a72c30b8b02e5cc493abf72f56f7718c90", PasswordHasher.encode("Zz#Kubo2026", "1234"));
                    equal("3b8401baab398427cb5a7ceec1f3a6bba036d5e7", PasswordHasher.encode("NewP@ss2026", "aLongLegacyShapedSalt32chars__"));
                    equal("f6030770a08943b5fe9ff9969af3788f6143f673", PasswordHasher.encode("password", "salt123"));
                }),
                new UtilityContracts.Case("password/output-format-is-40-hex-lowercase", () -> {
                    String hash = PasswordHasher.encode("complex#P@ss1", "salt");
                    equal(40, hash.length());
                    check(hash.matches("^[0-9a-f]{40}$"));
                }),
                new UtilityContracts.Case("password/empty-or-null-password-rejected", () -> {
                    for (String blank : new String[]{null, "", "   ", "\t\n"}) {
                        BusinessException error = fails(BusinessException.class, () -> PasswordHasher.encode(blank, "salt"));
                        equal(4001, error.getCode());
                        equal("密码不能为空", error.getMessage());
                    }
                }),
                new UtilityContracts.Case("password/empty-or-null-salt-rejected", () -> {
                    for (String blank : new String[]{null, "", "   ", "\t\n"}) {
                        BusinessException error = fails(BusinessException.class, () -> PasswordHasher.encode("Aa123456", blank));
                        equal(4001, error.getCode());
                        equal("盐不能为空", error.getMessage());
                    }
                }),
                new UtilityContracts.Case("password/unicode-password-and-salt", () -> {
                    String hash = PasswordHasher.encode("密码2026", "盐值");
                    equal(40, hash.length());
                    check(hash.matches("^[0-9a-f]{40}$"));
                    equal(hash, PasswordHasher.encode("密码2026", "盐值"));
                }),
                new UtilityContracts.Case("password/concurrent-callers", () -> {
                    var pool = Executors.newFixedThreadPool(4);
                    try {
                        List<Callable<Void>> tasks = new ArrayList<>();
                        for (int i = 0; i < 100; i++) {
                            tasks.add(() -> {
                                equal("1b7849727afe7bc11be3562248b9688f4c346e02", PasswordHasher.encode("Aa123456", "salt"));
                                return null;
                            });
                        }
                        for (var future : pool.invokeAll(tasks)) {
                            future.get();
                        }
                    } finally {
                        pool.shutdownNow();
                    }
                })
        );
    }
}
