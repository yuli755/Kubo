package cn.hamm.spms.module.personnel.user.token;

import cn.hamm.spms.common.config.ApiConfig;
import cn.hamm.spms.common.security.AccessTokenUtil;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.module.personnel.user.enums.UserTokenType;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static cn.hamm.spms.common.exception.Errors.FORBIDDEN_EXIST;
import static cn.hamm.spms.common.exception.Errors.FORBIDDEN;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
public class PersonalTokenService extends BaseService<PersonalTokenEntity, PersonalTokenRepository> {

    public static final String PERSONAL_TOKEN_NAME = "personal";

    /**
     * 私人令牌默认有效期：365 天（阶段 D-1）。
     *
     * <p>框架把 expire=0 判成「永不过期」，原实现从不设置 → 泄露的令牌（备份、
     * 日志、离职人员的脚本）是永久有效的完整权限凭据，只能靠人肉禁用止损。
     * 给新令牌统一压上限，撤销从「无限期追杀」变成「最多再活一年」。
     * 存量 0 值令牌行为不变，由禁用/删除通道处置。</p>
     */
    public static final long PERSONAL_TOKEN_EXPIRE_SECONDS = 365L * 24 * 60 * 60;

    /**
     * 单用户令牌数量上限（阶段 D-2）：原名唯一挡不住改名刷令牌，
     * 成百上千枚活跃令牌 = 成百上千个撤销点，吊销纪律必然失守。
     */
    public static final long MAX_TOKENS_PER_USER = 20;

    @Autowired
    private ApiConfig apiConfig;

    /**
     * 根据令牌获取
     *
     * @param token 令牌
     * @return 令牌
     */
    public PersonalTokenEntity getByToken(String token) {
        return repository.getByToken(token);
    }

    @Override
    protected @NotNull PersonalTokenEntity beforeAdd(@NotNull PersonalTokenEntity personalToken) {
        List<PersonalTokenEntity> list = filter(new PersonalTokenEntity().setUser(personalToken.getUser()));
        FORBIDDEN.when(list.size() >= MAX_TOKENS_PER_USER,
                String.format("私人令牌数量已达上限（%d），请先删除不再使用的令牌", MAX_TOKENS_PER_USER));
        boolean nameDuplicated = list.stream()
                .anyMatch(exist -> exist.getName() != null && exist.getName().equals(personalToken.getName()));
        FORBIDDEN_EXIST.when(nameDuplicated, "创建失败，该用户存在相同名称的私人令牌！");
        personalToken.setToken(createToken(personalToken.getUser().getId()));
        return personalToken;
    }

    @Override
    protected @NotNull PersonalTokenEntity beforeUpdate(@NotNull PersonalTokenEntity personalToken) {
        personalToken.setToken(null);
        return personalToken;
    }

    /**
     * 创建私人令牌
     *
     * @return AppKey
     */
    public final String createToken(long userId) {
        String token = AccessTokenUtil.create().setPayloadId(userId)
                .addPayload(UserTokenType.TYPE, UserTokenType.PERSONAL.getKey())
                .addPayload(PERSONAL_TOKEN_NAME, Math.random())
                .setExpireSecond(PERSONAL_TOKEN_EXPIRE_SECONDS)
                .build(apiConfig.getAccessTokenSecret());
        PersonalTokenEntity openApp = getByToken(token);
        FORBIDDEN_EXIST.whenNotNull(openApp, "创建失败，私人令牌重复！");
        return token;
    }
}
