package cn.hamm.spms.core;

/*
 * 来源：AirPower-Core 6.3.1（https://github.com/AirPowerTeam/AirPower-Core ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场【core 层自持】：整模块搬入本仓，仅把包名 cn.hamm.airpower.core → cn.hamm.spms.core。
 * 逻辑一字未改；本仓从此自己拥有这一层（上游变化不再自动进入）。改代码改这里。
 *
 * 为什么后来改成「整模块」而不是只搬调用闭包：框架层（curd/api/redis）也搬进来了，
 * 它要用的核心类必须都在 —— 半套副本只会把「缺哪个类」变成编译期的猜谜（见 docs 的可行性矩阵）。
 */

import cn.hamm.spms.core.exception.ServiceException;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.crypto.Cipher.DECRYPT_MODE;
import static javax.crypto.Cipher.ENCRYPT_MODE;

/**
 * <h1>AES 工具类</h1>
 *
 * @author Hamm.cn
 */
@Accessors(chain = true)
public class AesUtil {
    /**
     * 加密算法
     */
    @Setter(AccessLevel.NONE)
    private String algorithm = "AES";

    /**
     * 密钥
     */
    @Setter
    private String key;

    /**
     * 偏移向量
     */
    @Setter
    private String iv = "0000000000000000";

    /**
     * 工作模式
     */
    @Setter(AccessLevel.NONE)
    private String mode = "CBC";

    /**
     * 填充模式
     */
    @Setter
    private String padding = "PKCS5Padding";

    /**
     * Cipher 缓存（按加密模式缓存）
     */
    private final java.util.concurrent.ConcurrentHashMap<Integer, Cipher> cipherCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 禁止外部实例化
     */
    @Contract(pure = true)
    private AesUtil() {

    }

    /**
     * 创建实例
     *
     * @return 新实例
     */
    @Contract(" -> new")
    public static @NotNull AesUtil create() {
        return new AesUtil();
    }

    /**
     * 加密
     *
     * @param source 待加密的内容
     * @return 加密后的内容
     */
    public final String encrypt(String source) {
        if (Objects.isNull(source)) {
            throw new ServiceException("加密内容不能为null");
        }
        try {
            return Base64.getEncoder().encodeToString(getCipher(ENCRYPT_MODE)
                    .doFinal(source.getBytes(UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 解密
     *
     * @param content 加密后的内容
     * @return 解密后的内容
     */
    @Contract("_ -> new")
    public final @NotNull String decrypt(String content) {
        if (Objects.isNull(content)) {
            throw new ServiceException("解密内容不能为null");
        }
        try {
            return new String(getCipher(DECRYPT_MODE)
                    .doFinal(Base64.getDecoder().decode(content)), UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取 {@code Cipher}
     *
     * @param type 模式
     * @return {@code Cipher}
     */
    private @NotNull Cipher getCipher(int type) {
        return cipherCache.computeIfAbsent(type, t -> {
            try {
                SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(UTF_8), algorithm);
                IvParameterSpec ivParameterSpec = new IvParameterSpec(iv.getBytes(UTF_8));
                Cipher cipher = Cipher.getInstance(algorithm + "/" + mode + "/" + padding);
                cipher.init(t, secretKeySpec, ivParameterSpec);
                return cipher;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
