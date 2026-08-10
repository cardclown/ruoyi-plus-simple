package org.dromara.content.domain.bo;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ContentArticleStatusBoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsIdAndPublishedStatus() {
        ContentArticleStatusBo bo = statusBo(100L, "1");

        assertThat(validator.validate(bo)).isEmpty();
    }

    @Test
    void rejectsMissingArticleId() {
        assertThat(validator.validate(statusBo(null, "1")))
            .anyMatch(violation -> "articleId".equals(violation.getPropertyPath().toString()));
    }

    @Test
    void rejectsMissingStatus() {
        assertThat(validator.validate(statusBo(100L, null)))
            .anyMatch(violation -> "status".equals(violation.getPropertyPath().toString()));
    }

    @Test
    void rejectsUnknownStatus() {
        assertThat(validator.validate(statusBo(100L, "2")))
            .anyMatch(violation -> "status".equals(violation.getPropertyPath().toString()));
    }

    private static ContentArticleStatusBo statusBo(Long articleId, String status) {
        ContentArticleStatusBo bo = new ContentArticleStatusBo();
        bo.setArticleId(articleId);
        bo.setStatus(status);
        return bo;
    }
}
