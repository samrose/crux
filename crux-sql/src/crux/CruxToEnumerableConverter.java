package crux.calcite;

import org.apache.calcite.adapter.enumerable.EnumerableRel;
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor;
import org.apache.calcite.adapter.enumerable.JavaRowFormat;
import org.apache.calcite.adapter.enumerable.PhysType;
import org.apache.calcite.adapter.enumerable.PhysTypeImpl;
import org.apache.calcite.linq4j.tree.BlockBuilder;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.linq4j.tree.MethodCallExpression;
import org.apache.calcite.plan.ConventionTraitDef;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCost;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.convert.ConverterImpl;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.util.BuiltInMethod;
import org.apache.calcite.util.Pair;

// import java.util.AbstractList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Relational expression representing a scan of a table in an Elasticsearch data source.
 */
public class CruxToEnumerableConverter extends ConverterImpl implements EnumerableRel {
    CruxToEnumerableConverter(RelOptCluster cluster, RelTraitSet traits, RelNode input) {
        super(cluster, ConventionTraitDef.INSTANCE, traits, input);
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
        return new CruxToEnumerableConverter(getCluster(), traitSet, sole(inputs));
    }

    @Override public RelOptCost computeSelfCost(RelOptPlanner planner, RelMetadataQuery mq) {
        return super.computeSelfCost(planner, mq).multiplyBy(.1);
    }

    @Override public Result implement(EnumerableRelImplementor relImplementor, Prefer pref) {
        final BlockBuilder block = new BlockBuilder();
        final CruxRel.Implementor implementor = new CruxRel.Implementor();
        implementor.visitChild(0, getInput());

        final RelDataType rowType = getRowType();
        final PhysType physType =
            PhysTypeImpl.of(
                            relImplementor.getTypeFactory(), rowType,
                            pref.prefer(JavaRowFormat.ARRAY));

        final Expression table = block.append("table",
                                              implementor.table
                                              .getExpression(CruxTable.CruxQueryable.class));

        String schemaEdn = (String) CruxUtils.resolve("clojure.core/prn-str").invoke(implementor.schema);
        final Expression schema = block.append("schema", Expressions.constant(schemaEdn));

        Expression enumerable = block.append("enumerable",
                                             Expressions.call(table,
                                                              CruxMethod.CRUX_QUERYABLE_FIND.method, schema));

        // if (CalciteSystemProperty.DEBUG.value()) {
        //     System.out.println("Mongo: " + opList);
        // }
        // Hook.QUERY_PLAN.run(opList);

        block.add(Expressions.return_(null, enumerable));
        return relImplementor.result(physType, block.toBlock());
    }
}
