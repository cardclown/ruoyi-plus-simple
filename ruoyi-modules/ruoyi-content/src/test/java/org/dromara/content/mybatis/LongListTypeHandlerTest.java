package org.dromara.content.mybatis;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class LongListTypeHandlerTest {

    private final LongListTypeHandler handler = new LongListTypeHandler();

    @Test
    void writesIdsAsCommaSeparatedText() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);

        handler.setNonNullParameter(statement, 1, List.of(9L, 7L), JdbcType.VARCHAR);

        verify(statement).setString(1, "9,7");
    }

    @Test
    void writesEmptyListAsSqlNull() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);

        handler.setNonNullParameter(statement, 1, List.of(), JdbcType.VARCHAR);

        verify(statement).setNull(1, Types.VARCHAR);
    }

    @Test
    void readsCommaSeparatedTextAsIds() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("tag_ids")).thenReturn("9,7");

        assertThat(handler.getNullableResult(resultSet, "tag_ids"))
            .containsExactly(9L, 7L);
    }

    @Test
    void readsNullAsEmptyList() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("tag_ids")).thenReturn(null);

        assertThat(handler.getNullableResult(resultSet, "tag_ids")).isEmpty();
    }

    @Test
    void rejectsMalformedStoredValue() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("tag_ids")).thenReturn("9,broken");

        assertThatThrownBy(() -> handler.getNullableResult(resultSet, "tag_ids"))
            .isInstanceOf(TypeException.class)
            .hasMessageContaining("标签ID数据格式不正确");
    }
}
