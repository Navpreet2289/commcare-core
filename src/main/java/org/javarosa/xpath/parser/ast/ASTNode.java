package org.javarosa.xpath.parser.ast;

import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.parser.Token;
import org.javarosa.xpath.parser.XPathSyntaxException;

import java.util.Enumeration;
import java.util.List;

public abstract class ASTNode {
    public abstract List<? extends ASTNode> getChildren();

    public abstract XPathExpression build() throws XPathSyntaxException;

    private int indent;

    private void printStr(String s) {
        for (int i = 0; i < 2 * indent; i++)
            System.out.print(" ");
        System.out.println(s);
    }

    public void print(Object o) {
        indent += 1;

        if (o instanceof ASTNodeAbstractExpr) {
            ASTNodeAbstractExpr x = (ASTNodeAbstractExpr)o;
            printStr("abstractexpr {");
            for (int i = 0; i < x.size(); i++) {
                if (x.getType(i) == ASTNodeAbstractExpr.CHILD)
                    print(x.content.get(i));
                else
                    printStr(x.getToken(i).toString());
            }
            printStr("}");
        } else if (o instanceof ASTNodePredicate) {
            ASTNodePredicate x = (ASTNodePredicate)o;
            printStr("predicate {");
            print(x.expr);
            printStr("}");
        } else if (o instanceof ASTNodeFunctionCall) {
            ASTNodeFunctionCall x = (ASTNodeFunctionCall)o;
            if (x.args.size() == 0) {
                printStr("func {" + x.name.toString() + ", args {none}}");
            } else {
                printStr("func {" + x.name.toString() + ", args {{");
                for (int i = 0; i < x.args.size(); i++) {
                    print(x.args.get(i));
                    if (i < x.args.size() - 1)
                        printStr(" } {");
                }
                printStr("}}}");
            }
        } else if (o instanceof ASTNodeBinaryOp) {
            ASTNodeBinaryOp x = (ASTNodeBinaryOp)o;
            printStr("opexpr {");
            for (int i = 0; i < x.exprs.size(); i++) {
                print(x.exprs.get(i));
                if (i < x.exprs.size() - 1) {
                    switch (x.ops.get(i)) {
                        case Token.AND:
                            printStr("and:");
                            break;
                        case Token.OR:
                            printStr("or:");
                            break;
                        case Token.EQ:
                            printStr("eq:");
                            break;
                        case Token.NEQ:
                            printStr("neq:");
                            break;
                        case Token.LT:
                            printStr("lt:");
                            break;
                        case Token.LTE:
                            printStr("lte:");
                            break;
                        case Token.GT:
                            printStr("gt:");
                            break;
                        case Token.GTE:
                            printStr("gte:");
                            break;
                        case Token.PLUS:
                            printStr("plus:");
                            break;
                        case Token.MINUS:
                            printStr("minus:");
                            break;
                        case Token.DIV:
                            printStr("div:");
                            break;
                        case Token.MOD:
                            printStr("mod:");
                            break;
                        case Token.MULT:
                            printStr("mult:");
                            break;
                        case Token.UNION:
                            printStr("union:");
                            break;
                    }
                }
            }
            printStr("}");
        } else if (o instanceof ASTNodeUnaryOp) {
            ASTNodeUnaryOp x = (ASTNodeUnaryOp)o;
            printStr("opexpr {");
            switch (x.op) {
                case Token.UMINUS:
                    printStr("num-neg:");
                    break;
            }
            print(x.expr);
            printStr("}");
        } else if (o instanceof ASTNodeLocPath) {
            ASTNodeLocPath x = (ASTNodeLocPath)o;
            printStr("pathexpr {");
            int offset = x.isAbsolute() ? 1 : 0;
            for (int i = 0; i < x.clauses.size() + offset; i++) {
                if (offset == 0 || i > 0)
                    print(x.clauses.elementAt(i - offset));
                if (i < x.separators.size()) {
                    switch (x.separators.get(i)) {
                        case Token.DBL_SLASH:
                            printStr("dbl-slash:");
                            break;
                        case Token.SLASH:
                            printStr("slash:");
                            break;
                    }
                }
            }
            printStr("}");

        } else if (o instanceof ASTNodePathStep) {
            ASTNodePathStep x = (ASTNodePathStep)o;
            printStr("step {axis: " + x.axisType + " node test type: " + x.nodeTestType);
            if (x.axisType == ASTNodePathStep.AXIS_TYPE_EXPLICIT)
                printStr("  axis type: " + x.axisVal);
            if (x.nodeTestType == ASTNodePathStep.NODE_TEST_TYPE_QNAME)
                printStr("  node test name: " + x.nodeTestQName.toString());
            if (x.nodeTestType == ASTNodePathStep.NODE_TEST_TYPE_FUNC) print(x.nodeTestFunc);
            printStr("predicates...");
            for (Enumeration e = x.predicates.elements(); e.hasMoreElements(); )
                print(e.nextElement());
            printStr("}");
        } else if (o instanceof ASTNodeFilterExpr) {
            ASTNodeFilterExpr x = (ASTNodeFilterExpr)o;
            printStr("filter expr {");
            print(x.expr);
            printStr("predicates...");
            for (Enumeration e = x.predicates.elements(); e.hasMoreElements(); )
                print(e.nextElement());
            printStr("}");
        }

        indent -= 1;
    }
}