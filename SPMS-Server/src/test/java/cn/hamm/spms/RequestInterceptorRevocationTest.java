package cn.hamm.spms;

import cn.hamm.spms.core.exception.ServiceException;
import cn.hamm.spms.common.security.AccessTokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * <h1>RequestInterceptor 登出吊销拦截测试</h1>
 *
 * <p>只钉吊销这一道闸门的契约：命中名单的令牌在<b>验签之前</b>就被拒绝
 * （报"登录状态已失效"而非"令牌无效"，让前端能区分"重新登录即可"与"凭证本身坏了"）；
 * 吊销检查发生在剥离 {@code Bearer } 前缀<b>之后</b>——否则登出写黑名单用裸令牌、
 * 鉴权查名单用带前缀原文，两把 key 对不上，吊销等于没做。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("RequestInterceptor · 登出令牌吊销拦截")
@ExtendWith(MockitoExtension.class)
class RequestInterceptorRevocationTest {

    @Mock
    private AccessTokenRevocationService accessTokenRevocationService;

    private RequestInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RequestInterceptor();
        injectField(interceptor, "accessTokenRevocationService", accessTokenRevocationService);
    }

    @Test
    @DisplayName("已吊销的裸令牌：验签前直接拒绝，提示重新登录")
    void revokedTokenRejectedBeforeSignatureCheck() {
        given(accessTokenRevocationService.isRevoked("tok-1")).willReturn(true);

        ServiceException e = assertThrows(ServiceException.class,
                () -> interceptor.getVerifiedToken("tok-1"));
        assertTrue(e.getMessage().contains("登录状态已失效"),
                "必须报『登录状态已失效』而不是笼统的令牌无效，实际: " + e.getMessage());
    }

    @Test
    @DisplayName("已吊销：Bearer 前缀先剥离，名单查询用裸令牌（与 logout 侧同 key）")
    void bearerPrefixStrippedBeforeRevocationLookup() {
        given(accessTokenRevocationService.isRevoked("tok-1")).willReturn(true);

        // 严格桩只认 isRevoked("tok-1")：若拦截器拿 "Bearer tok-1" 去查名单，
        // 这里既不会命中桩（返回 false 走验签），也会因"桩从未被消费"而红。
        assertThrows(ServiceException.class,
                () -> interceptor.getVerifiedToken("Bearer tok-1"));

        verify(accessTokenRevocationService).isRevoked("tok-1");
        verify(accessTokenRevocationService, never()).isRevoked("Bearer tok-1");
    }

    /** 字段注入的代价：协作对象靠反射塞（同 RequestInterceptorPermissionContextTest） */
    private static void injectField(Object target, String fieldName, Object value) {
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            throw new NoSuchFieldException(fieldName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
