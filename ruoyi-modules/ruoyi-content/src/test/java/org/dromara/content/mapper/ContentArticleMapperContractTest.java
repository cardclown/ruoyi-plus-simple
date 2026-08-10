package org.dromara.content.mapper;

import org.dromara.common.mybatis.annotation.DataColumn;
import org.dromara.common.mybatis.annotation.DataPermission;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ContentArticleMapperContractTest {

    @Test
    void allArticleReadAndWriteEntryPointsApplyCreatorDataPermission() {
        for (String methodName : new String[]{
            "selectArticleById",
            "selectArticlePage",
            "selectArticleList",
            "selectExistingArticleIds",
            "updateArticleById",
            "updateArticleStatus",
            "logicalDeleteByIds",
            "selectDeletedArticleIds",
            "physicalDeleteByIds"
        }) {
            Method method = Arrays.stream(ContentArticleMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
            DataPermission permission = method.getAnnotation(DataPermission.class);
            assertThat(permission).as(methodName).isNotNull();
            assertThat(permission.value())
                .extracting(
                    column -> column.key()[0],
                    column -> column.value()[0]
                )
                .containsExactlyInAnyOrder(
                    org.assertj.core.groups.Tuple.tuple("deptName", "create_dept"),
                    org.assertj.core.groups.Tuple.tuple("userName", "create_by")
                );
        }
    }
}
