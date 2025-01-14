package org.hswebframework.web.dao.mybatis.mapper.dict;

import org.hswebframework.ezorm.core.OptionConverter;
import org.hswebframework.ezorm.core.param.Term;
import org.hswebframework.ezorm.core.param.TermType;
import org.hswebframework.ezorm.rdb.meta.RDBColumnMetaData;
import org.hswebframework.ezorm.rdb.render.SqlAppender;
import org.hswebframework.ezorm.rdb.render.dialect.Dialect;
import org.hswebframework.ezorm.rdb.render.dialect.RenderPhase;
import org.hswebframework.ezorm.rdb.render.dialect.function.SqlFunction;
import org.hswebframework.ezorm.rdb.render.dialect.term.BoostTermTypeMapper;
import org.hswebframework.web.dao.mybatis.mapper.AbstractSqlTermCustomer;
import org.hswebframework.web.dao.mybatis.mapper.ChangedTermValue;
import org.hswebframework.web.dict.EnumDict;

import java.sql.JDBCType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hswebframework.web.dao.mybatis.mapper.dict.DictInTermTypeMapper.USE_DICT_MASK_FLAG;

/**
 * @author zhouhao
 * @since 3.0.0-RC
 */
public class DictTermTypeMapper extends AbstractSqlTermCustomer {

    private boolean not;

    public DictTermTypeMapper(boolean not) {
        super(not ? TermType.not : TermType.eq);
        this.not = not;
    }

    private boolean support(RDBColumnMetaData column) {
        Class type = column.getJavaType();
        if (type.isArray()) {
            type = type.getComponentType();
        }
        return ((type.isEnum() && EnumDict.class.isAssignableFrom(type))
                ||
                (column.getProperty(USE_DICT_MASK_FLAG).isTrue() && column.getOptionConverter() != null));
    }

    @SuppressWarnings("all")
    private List<EnumDict> getAllOption(RDBColumnMetaData column) {
        Class type = column.getJavaType();
        if (null != type) {
            if (type.isArray()) {
                type = type.getComponentType();
            }
            if (type.isEnum() && EnumDict.class.isAssignableFrom(type)) {
                return (List) Arrays.asList(type.getEnumConstants());
            }
        }

        OptionConverter converter = column.getOptionConverter();
        if (converter == null) {
            return Collections.emptyList();
        }

        return (List) converter.getOptions();
    }

    @Override
    public SqlAppender accept(String wherePrefix, Term term, RDBColumnMetaData column, String tableAlias) {
        //不支持数据字典
        if (!support(column)) {
            return buildNotSupport(wherePrefix, term, column, tableAlias);
        }
        ChangedTermValue changedValue = createChangedTermValue(term);

        List<Object> list = BoostTermTypeMapper.convertList(column, changedValue.getOld());

        EnumDict[] dicts = getAllOption(column)
                .stream()
                .filter(d -> d.eq(list))
                .toArray(EnumDict[]::new);

        changedValue.setValue(EnumDict.toMask(dicts));
        Dialect dialect = column.getTableMetaData().getDatabaseMetaData().getDialect();
        String columnName = dialect.buildColumnName(tableAlias, column.getName());
        return new SqlAppender().add(columnName, not ? " != " : "=", "#{", wherePrefix, ".value.value}");
    }

    protected SqlAppender buildNotSupport(String wherePrefix, Term term, RDBColumnMetaData column, String tableAlias) {
        createChangedTermValue(term);
        Dialect dialect = column.getTableMetaData().getDatabaseMetaData().getDialect();
        String columnName = dialect.buildColumnName(tableAlias, column.getName());
        SqlAppender appender = new SqlAppender();
        appender.add(columnName, not ? " != " : "=", "#{", wherePrefix, ".value.value}");
        return appender;
    }
}
