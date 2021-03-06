package org.javarosa.xpath.analysis;

import org.javarosa.core.model.instance.TreeReference;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.annotations.Nullable;

/**
 * A type of XPathAnalyzer which collects and aggregates a specified type of information from
 * wherever it is present in the expression.
 *
 * IMPORTANT NOTE: An accumulating analyzer may analyze the same sub-expression or context ref of
 * an expression multiple times in a single analysis pass. This means:
 * - An AccumulatingAnalyzer is NOT appropriate to use for answering questions such as
 * "How many times is X referenced in this expression?"
 * - An accumulating analyzer IS appropriate to use for answering questions such as
 * "What is the set of all things of X type which are referenced at least one time in this expression?"
 *
 * @author Aliza Stone
 */
public abstract class XPathAccumulatingAnalyzer<T> extends XPathAnalyzer {

    private List<T> accumulatedList = new ArrayList<>();

    @Nullable
    public Set<T> accumulate(XPathAnalyzable rootExpression) {
        try {
            rootExpression.applyAndPropagateAnalyzer(this);
            Set<T> set = new HashSet<>();
            for (T item : aggregateResults(new ArrayList<T>())) {
                set.add(item);
            }
            return set;
        } catch (AnalysisInvalidException e) {
            return null;
        }
    }

    protected void addResultToList(T t) {
        accumulatedList.add(t);
    }

    private List<T> aggregateResults(List<T> aggregated) {
        aggregated.addAll(this.accumulatedList);
        for (XPathAnalyzer subAnalyzer : this.subAnalyzers) {
            ((XPathAccumulatingAnalyzer)subAnalyzer).aggregateResults(aggregated);
        }
        return aggregated;
    }

    // FOR TESTING PURPOSES ONLY -- This can NOT be relied upon to not return duplicates in certain scenarios
    @Nullable
    public List<T> accumulateAsList(XPathAnalyzable rootExpression) {
        try {
            rootExpression.applyAndPropagateAnalyzer(this);
            return aggregateResults(new ArrayList<T>());
        } catch (AnalysisInvalidException e) {
            return null;
        }
    }

    // This implementation should work for most accumulating analyzers, but some subclasses may want
    // to override and provide more specific behavior
    @Override
    public void doAnalysisForTreeRefWithCurrent(TreeReference expressionWithContextTypeCurrent)
            throws AnalysisInvalidException {
        requireOriginalContext(expressionWithContextTypeCurrent);
        doNormalTreeRefAnalysis(expressionWithContextTypeCurrent.contextualize(getOriginalContextRef()));
    }

    // This implementation should work for most accumulating analyzers, but some subclasses may want
    // to override and provide more specific behavior
    @Override
    public void doAnalysisForRelativeTreeRef(TreeReference expressionWithContextTypeRelative)
            throws AnalysisInvalidException {
        requireContext(expressionWithContextTypeRelative);
        doNormalTreeRefAnalysis(expressionWithContextTypeRelative.contextualize(this.getContextRef()));
    }

}
