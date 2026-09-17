package cn.hamm.spms.framework.api;

/*
 * 来源：AirPower4J 6.4.3（https://github.com/AirPowerTeam/AirPower4J ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场 —【框架层自持】整模块搬入本仓，只改包名：
 *   cn.hamm.airpower.core  → cn.hamm.spms.core
 *   cn.hamm.airpower.api   → cn.hamm.spms.framework.api
 *   cn.hamm.airpower.curd  → cn.hamm.spms.framework.curd
 *   cn.hamm.airpower.redis → cn.hamm.spms.framework.redis
 * 代码逻辑一字未改（JPA→MyBatis-Plus 的持久化替换是紧随其后的独立一批，见 docs）。
 * 从此这一层由本仓拥有：改代码改这里，不再有"上游"。
 */

import cn.hamm.spms.framework.api.config.ApiConfig;
import cn.hamm.spms.core.AccessTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

/**
 * <h1>控制器根类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Controller
public class ApiController {
    @Autowired
    protected ApiConfig apiConfig;

    @Autowired
    protected HttpServletRequest request;

    /**
     * 获取当前登录用户的信息
     *
     * @return 用户 ID
     */
    protected final long getCurrentUserId() {
        String accessToken = request.getHeader(apiConfig.getAuthorizeHeader());
        AccessTokenUtil.VerifiedToken verifiedToken = AccessTokenUtil.create().verify(
                accessToken,
                apiConfig.getAccessTokenSecret()
        );
        return verifiedToken.getPayloadId();
    }
}
