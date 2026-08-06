package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.captcha.generator.CodeGenerator;
import cn.hutool.captcha.generator.MathGenerator;
import cn.hutool.captcha.generator.RandomGenerator;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.Constants;
import org.dromara.common.core.constant.GlobalConstants;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.ratelimiter.annotation.RateLimiter;
import org.dromara.common.ratelimiter.enums.LimitType;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.web.core.WaveAndCircleCaptcha;
import org.dromara.common.web.config.properties.CaptchaProperties;
import org.dromara.web.domain.vo.CaptchaVo;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.awt.*;
import java.time.Duration;

/**
 * 验证码操作处理
 *
 * @author Lion Li
 */
@SaIgnore
@RequiredArgsConstructor
@RestController
public class CaptchaController {

    private final CaptchaProperties captchaProperties;

    /**
     * 生成验证码
     */
    @GetMapping("/auth/code")
    public R<CaptchaVo> getCode() {
        boolean captchaEnabled = captchaProperties.getEnable();
        if (!captchaEnabled) {
            CaptchaVo captchaVo = new CaptchaVo();
            captchaVo.setCaptchaEnabled(false);
            return R.ok(captchaVo);
        }
        return R.ok(SpringUtils.getAopProxy(this).getCodeImpl());
    }

    /**
     * 生成验证码
     * 独立方法避免验证码关闭之后仍然走限流
     */
    @RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
    public CaptchaVo getCodeImpl() {
        // 保存验证码信息
        String uuid = IdUtil.simpleUUID();
        String verifyKey = GlobalConstants.CAPTCHA_CODE_KEY + uuid;
        // 生成验证码
        String captchaType = captchaProperties.getType();
        CodeGenerator codeGenerator;
        if ("math".equals(captchaType)) {
            codeGenerator = new MathGenerator(captchaProperties.getNumberLength(), false);
        } else {
            codeGenerator = new RandomGenerator(captchaProperties.getCharLength());
        }
        WaveAndCircleCaptcha captcha = new WaveAndCircleCaptcha(160, 60);
        // captcha.setBackground(Color.WHITE); // 不设置就是透明底
        captcha.setFont(new Font("Arial", Font.BOLD, 45));
        captcha.setGenerator(codeGenerator);
        captcha.createCode();
        // 如果是数学验证码，使用SpEL表达式处理验证码结果
        String code = captcha.getCode();
        if ("math".equals(captchaType)) {
            ExpressionParser parser = new SpelExpressionParser();
            Expression exp = parser.parseExpression(StringUtils.remove(code, "="));
            code = exp.getValue(String.class);
        }
        RedisUtils.setCacheObject(verifyKey, code, Duration.ofMinutes(Constants.CAPTCHA_EXPIRATION));
        CaptchaVo captchaVo = new CaptchaVo();
        captchaVo.setUuid(uuid);
        captchaVo.setImg(captcha.getImageBase64());
        return captchaVo;
    }

}
