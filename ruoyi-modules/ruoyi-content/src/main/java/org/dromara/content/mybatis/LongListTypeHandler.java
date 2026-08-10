package org.dromara.content.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeException;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

/**
 * 在 Java 的 {@code List<Long>} 与数据库逗号分隔字符串之间转换。
 * 空集合写为 SQL NULL，读取 NULL 或空白值时返回空集合；任何空元素或非数字内容均拒绝处理。
 */
public class LongListTypeHandler extends BaseTypeHandler<List<Long>> {

    /**
     * 将标签 ID 列表写入预编译语句。
     *
     * @param ps 预编译语句
     * @param i 参数索引
     * @param parameter 待写入的标签 ID 列表
     * @param jdbcType JDBC 类型
     * @throws SQLException 写入数据库失败时抛出
     * @throws TypeException 列表包含空元素时抛出
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<Long> parameter, JdbcType jdbcType)
        throws SQLException {
        if (parameter.isEmpty()) {
            // 数据库使用 NULL 表示“无标签”，避免保存空字符串。
            ps.setNull(i, Types.VARCHAR);
            return;
        }
        StringJoiner joiner = new StringJoiner(",");
        for (Long value : parameter) {
            if (value == null) {
                throw new TypeException("标签ID不能为空");
            }
            joiner.add(value.toString());
        }
        ps.setString(i, joiner.toString());
    }

    /**
     * 按列名读取并解析标签 ID 列表。
     *
     * @param rs 查询结果集
     * @param columnName 列名
     * @return 解析出的标签 ID 列表；NULL 或空白值返回空集合
     * @throws SQLException 读取结果集失败时抛出
     * @throws TypeException 列值含有空元素或非数字内容时抛出
     */
    @Override
    public List<Long> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    /**
     * 按列索引读取并解析标签 ID 列表。
     *
     * @param rs 查询结果集
     * @param columnIndex 列索引
     * @return 解析出的标签 ID 列表；NULL 或空白值返回空集合
     * @throws SQLException 读取结果集失败时抛出
     * @throws TypeException 列值含有空元素或非数字内容时抛出
     */
    @Override
    public List<Long> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    /**
     * 从存储过程输出参数读取并解析标签 ID 列表。
     *
     * @param cs 可调用语句
     * @param columnIndex 输出参数索引
     * @return 解析出的标签 ID 列表；NULL 或空白值返回空集合
     * @throws SQLException 读取输出参数失败时抛出
     * @throws TypeException 输出参数含有空元素或非数字内容时抛出
     */
    @Override
    public List<Long> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    /**
     * 将数据库逗号分隔字符串解析为标签 ID 列表。
     *
     * @param value 数据库读取的字符串
     * @return 标签 ID 列表；NULL 或空白值返回空集合
     * @throws TypeException 字符串包含空元素或非数字内容时抛出
     */
    private List<Long> parse(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        try {
            String[] values = value.split(",", -1);
            List<Long> result = new ArrayList<>(values.length);
            for (String item : values) {
                result.add(Long.valueOf(item.trim()));
            }
            return result;
        } catch (NumberFormatException ex) {
            throw new TypeException("标签ID数据格式不正确: " + value, ex);
        }
    }
}
