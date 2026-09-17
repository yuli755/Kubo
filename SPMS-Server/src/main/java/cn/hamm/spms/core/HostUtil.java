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

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * <h1>Host 工具类</h1>
 *
 * @author Hamm.cn
 */
public class HostUtil {
    /**
     * 获取服务器主机名的完整方法
     */
    public static String getHostName() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            if (isValidHostname(hostname)) {
                return hostname;
            }
        } catch (UnknownHostException e) {
            // 忽略异常，继续尝试其他方法
        }

        // 尝试系统属性
        String hostname = System.getProperty("hostname");
        if (isValidHostname(hostname)) {
            return hostname;
        }
        return getHostnameFromEnvironment();
    }

    /**
     * 从环境变量获取主机名
     */
    private static @Nullable String getHostnameFromEnvironment() {
        // Windows
        String hostname = System.getenv("COMPUTERNAME");
        if (isValidHostname(hostname)) {
            return hostname;
        }
        // Linux/Unix/Mac Docker
        hostname = System.getenv("HOSTNAME");
        if (isValidHostname(hostname)) {
            return hostname;
        }
        return null;
    }

    /**
     * 验证主机名是否有效
     */
    @Contract("null -> false")
    private static boolean isValidHostname(@Nullable String hostname) {
        return hostname != null && !hostname.trim().isEmpty();
    }
}
