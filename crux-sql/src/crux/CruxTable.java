package crux.calcite;

import org.apache.calcite.adapter.java.AbstractQueryableTable;
import org.apache.calcite.schema.TranslatableTable;
import org.apache.calcite.schema.impl.AbstractTableQueryable;
import org.apache.calcite.DataContext;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.linq4j.Queryable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.rel.RelFieldCollation;
import org.apache.calcite.util.Pair;
import crux.api.ICruxAPI;
import clojure.lang.Keyword;
import clojure.lang.IFn;
import java.util.Map;
import java.util.List;
import java.io.IOException;
import java.io.UncheckedIOException;

public class CruxTable extends AbstractQueryableTable implements TranslatableTable {
    ICruxAPI node;
    Map<Keyword, Object> schema;
    private final IFn scanFn;

    public CruxTable (ICruxAPI node, Map<Keyword, Object> schema) {
        super(Object[].class);
        this.node = node;
        this.schema = schema;
        this.scanFn = CruxUtils.resolve("crux.calcite/scan");
    }

    public RelDataType getRowType(RelDataTypeFactory typeFactory) {
        return (RelDataType)CruxUtils.resolve("crux.calcite/row-type").invoke(typeFactory, node, schema);
    }

    @SuppressWarnings("unchecked")
    public Enumerable<Object> find(String schema) {
        return (Enumerable<Object>) scanFn.invoke(node, schema);
    }

    @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider, SchemaPlus schema, String tableName) {
        return new CruxQueryable<>(queryProvider, schema, this, tableName);
    }

    @Override public RelNode toRel(RelOptTable.ToRelContext context, RelOptTable relOptTable) {
        final RelOptCluster cluster = context.getCluster();
        return new CruxTableScan(cluster, cluster.traitSetOf(CruxRel.CONVENTION), relOptTable, this, null);
    }

    public static class CruxQueryable<T> extends AbstractTableQueryable<T> {
        CruxQueryable(QueryProvider queryProvider, SchemaPlus schema,
                      CruxTable table, String tableName) {
            super(queryProvider, schema, table, tableName);
        }

        public Enumerator<T> enumerator() {
            return null;
        }

        private CruxTable getTable() {
            return (CruxTable) table;
        }

        public Enumerable<Object> find(String schema) {
            return getTable().find(schema);
        }
    }
}
