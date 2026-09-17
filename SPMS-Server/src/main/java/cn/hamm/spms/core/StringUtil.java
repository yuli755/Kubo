package cn.hamm.spms.core;

/*
 * 来源：AirPower-Core 6.3.1（https://github.com/AirPowerTeam/AirPower-Core ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场【core 层自持】：整模块搬入本仓，仅把包名 cn.hamm.airpower.core → cn.hamm.spms.core。
 * 逻辑一字未改；本仓从此自己拥有这一层（上游变化不再自动进入）。改代码改这里。
 *
 * 为什么后来改成「整模块」而不是只搬调用闭包：框架层（curd/api/redis）也搬进来了，
 * 它要用的核心类必须都在 —— 半套副本只会把「缺哪个类」变成编译期的猜谜（见 docs 的可行性矩阵）。
 */

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * <h1>字符串工具类</h1>
 *
 * @author Hamm.cn
 */
public class StringUtil {
    /**
     * 字符串是否为空
     *
     * @param str 字符串
     * @return 状态
     */
    @Contract("null -> true")
    public static boolean isEmpty(@Nullable CharSequence str) {
        return str == null || str.isEmpty();
    }

    /**
     * 字符串是否为空
     *
     * @param str 字符串
     * @return 状态
     */
    @Contract("null -> true")
    public static boolean isEmpty(@Nullable String str) {
        return str == null || str.isEmpty();
    }

    /**
     * 字符串是否包含有效字符
     *
     * @param str 字符串
     * @return 状态
     */
    @Contract("null -> false")
    public static boolean hasText(@Nullable CharSequence str) {
        if (str != null) {
            int strLen = str.length();
            if (strLen != 0) {
                for (int i = 0; i < strLen; ++i) {
                    if (!Character.isWhitespace(str.charAt(i))) {
                        return true;
                    }
                }

            }
        }
        return false;
    }

    /**
     * 字符串是否包含有效字符
     *
     * @param str 字符串
     * @return 状态
     */
    @Contract("null -> false")
    public static boolean hasText(@Nullable String str) {
        return str != null && !str.isBlank();
    }

    /**
     * 字符串是否包含空格
     *
     * @param str 字符串
     * @return 字符串
     */
    public static boolean containsWhitespace(@Nullable CharSequence str) {
        if (!isEmpty(str)) {
            int strLen = str.length();

            for (int i = 0; i < strLen; ++i) {
                if (Character.isWhitespace(str.charAt(i))) {
                    return true;
                }
            }

        }
        return false;
    }

    /**
     * 去除字符串中的空格
     *
     * @param str 字符串
     * @return 字符串
     */
    public static boolean containsWhitespace(@Nullable String str) {
        return containsWhitespace((CharSequence) str);
    }

    /**
     * 去除字符串中的空格
     *
     * @param str 字符串
     * @return 字符串
     */
    public static CharSequence trimAllWhitespace(CharSequence str) {
        if (isEmpty(str)) {
            return str;
        } else {
            int len = str.length();
            StringBuilder sb = new StringBuilder(str.length());

            for (int i = 0; i < len; ++i) {
                char c = str.charAt(i);
                if (!Character.isWhitespace(c)) {
                    sb.append(c);
                }
            }

            return sb;
        }
    }

    /**
     * 字符串首字母大写
     *
     * @param str 源字符串
     * @return 目标字符串
     */
    public static String capitalize(String str) {
        return changeFirstCharacterCase(str, true);
    }

    /**
     * 字符串首字母小写
     *
     * @param str 源字符串
     * @return 目标字符串
     */
    public static String uncapitalize(String str) {
        return changeFirstCharacterCase(str, false);
    }

    /**
     * 字符串首字母大小写转换
     *
     * @param str        源字符串
     * @param capitalize 是否大写
     * @return 目标字符串
     */
    private static String changeFirstCharacterCase(String str, boolean capitalize) {
        if (isEmpty(str)) {
            return str;
        } else {
            char baseChar = str.charAt(0);
            char updatedChar;
            if (capitalize) {
                updatedChar = Character.toUpperCase(baseChar);
            } else {
                updatedChar = Character.toLowerCase(baseChar);
            }

            if (baseChar == updatedChar) {
                return str;
            } else {
                char[] chars = str.toCharArray();
                chars[0] = updatedChar;
                return new String(chars);
            }
        }
    }
}
