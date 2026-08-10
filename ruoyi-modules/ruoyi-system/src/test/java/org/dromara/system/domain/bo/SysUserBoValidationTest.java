package org.dromara.system.domain.bo;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class SysUserBoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZhangSan", "zhangsan01", "13800138000", "ABC123"})
    void acceptsAsciiLettersAndDigits(String userName) {
        assertThat(userNameViolations(userName)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"张三", "abc_123", "abc-123", "abc 123"})
    void rejectsCharactersOutsideAsciiLettersAndDigits(String userName) {
        assertThat(userNameViolations(userName)).isNotEmpty();
    }

    @Test
    void rejectsUsernameShorterThanTwoCharacters() {
        assertThat(userNameViolations("a")).isNotEmpty();
    }

    @Test
    void rejectsUsernameLongerThanTwentyCharacters() {
        assertThat(userNameViolations("Abcdefghij12345678901")).isNotEmpty();
    }

    private static List<String> userNameViolations(String userName) {
        SysUserBo user = new SysUserBo();
        user.setUserName(userName);
        user.setNickName("测试用户");
        user.setRoleIds(new Long[]{1L});
        return validator.validate(user).stream()
            .filter(violation -> "userName".equals(violation.getPropertyPath().toString()))
            .map(violation -> violation.getMessage())
            .toList();
    }
}
