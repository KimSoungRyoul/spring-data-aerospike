/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.aerospike.query;

import com.aerospike.client.query.Filter;
import com.aerospike.client.query.Statement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.aerospike.query.cache.IndexesCache;
import org.springframework.data.aerospike.query.model.Index;
import org.springframework.data.aerospike.query.model.IndexedField;
import org.springframework.data.aerospike.query.qualifier.Qualifier;
import org.springframework.data.aerospike.repository.query.Query;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.springframework.data.aerospike.query.QualifierUtils.queryCriteriaIsNotNull;
import static org.springframework.data.aerospike.util.Utils.logQualifierDetails;

/**
 * @author peter
 * @author Anastasiia Smirnova
 */
@Slf4j
public class StatementBuilder {

    private final IndexesCache indexesCache;

    public StatementBuilder(IndexesCache indexesCache) {
        this.indexesCache = indexesCache;
    }

    public Statement build(String namespace, String set, Query query) {
        return build(namespace, set, query, null);
    }

    public Statement build(String namespace, String set, @Nullable Query query, String[] binNames) {
        Statement stmt = new Statement();
        stmt.setNamespace(namespace);
        stmt.setSetName(set);
        if (binNames != null && binNames.length != 0) {
            stmt.setBinNames(binNames);
        }
        if (queryCriteriaIsNotNull(query)) {
            // logging query
            logQualifierDetails(query.getCriteriaObject(), log);
            // statement's filter is set based either on cardinality (the lowest bin values ratio)
            // or on order (the first processed filter)
            setStatementFilterFromQualifiers(stmt, query.getCriteriaObject());
        }
        return stmt;
    }

    private void setStatementFilterFromQualifiers(Statement stmt, Qualifier qualifier) {
        // No qualifier, no need to set statement filter
        if (qualifier == null) {
            log.debug("Query #{}, secondary index filter is not set", qualifier.hashCode());
            return;
        }

        // Multiple qualifiers
        // No sense to use secondary index in case of OR as it requires to enlarge selection to more than 1 field
        if (qualifier.getOperation() == FilterOperation.AND) {
            setFilterFromMultipleQualifiers(stmt, qualifier);
        } else if (isIndexedBin(stmt, qualifier)) { // Single qualifier
            setFilterFromSingleQualifier(stmt, qualifier);
        }
        if (stmt.getFilter() != null) {
            log.debug("Query #{}, secondary index filter is set on the bin '{}'", qualifier.hashCode(),
                stmt.getFilter().getName());
        } else {
            log.debug("Query #{}, secondary index filter is not set", qualifier.hashCode());
        }
    }

    private void setFilterFromMultipleQualifiers(Statement stmt, Qualifier qualifier) {
        int minBinValuesRatio = Integer.MAX_VALUE;
        Qualifier minBinValuesRatioQualifier = null;

        for (Qualifier innerQualifier : qualifier.getQualifiers()) {
            if (innerQualifier != null && isIndexedBin(stmt, innerQualifier)) {
                int currBinValuesRatio = getMinBinValuesRatioForQualifier(stmt, innerQualifier);
                // Compare the cardinality of each qualifier and select the qualifier that has the index with
                // the lowest bin values ratio
                if (currBinValuesRatio != 0 && currBinValuesRatio < minBinValuesRatio) {
                    minBinValuesRatio = currBinValuesRatio;
                    minBinValuesRatioQualifier = innerQualifier;
                }
            }
        }

        // If index with min bin values ratio found, set filter with the matching qualifier
        if (minBinValuesRatioQualifier != null) {
            setFilterFromSingleQualifier(stmt, minBinValuesRatioQualifier);
        } else { // No index with bin values ratio found, do not consider cardinality when setting a filter
            for (Qualifier innerQualifier : qualifier.getQualifiers()) {
                if (innerQualifier != null && isIndexedBin(stmt, innerQualifier)) {
                    Filter filter = innerQualifier.getSecondaryIndexFilter();
                    if (filter != null) {
                        stmt.setFilter(filter);
                        innerQualifier.setHasSecIndexFilter(true);
                        break; // the filter from the first processed qualifier becomes statement's sIndex filter
                    }
                }
            }
        }
    }

    private void setFilterFromSingleQualifier(Statement stmt, Qualifier qualifier) {
        Filter filter = qualifier.getSecondaryIndexFilter();
        if (filter != null) {
            stmt.setFilter(filter);
            qualifier.setHasSecIndexFilter(true);
        }
    }

    private boolean isIndexedBin(Statement stmt, Qualifier qualifier) {
        boolean hasIndexesForField = false, hasField = false;
        if (StringUtils.hasLength(qualifier.getBinName())) {
            hasField = true;
            hasIndexesForField = indexesCache.hasIndexFor(
                new IndexedField(stmt.getNamespace(), stmt.getSetName(), qualifier.getBinName())
            );
        }

        if (log.isDebugEnabled() && hasField) {
            log.debug("Qualifier #{}, bin {}.{}.{} has secondary index(es): {}", qualifier.hashCode(),
                stmt.getNamespace(), stmt.getSetName(), qualifier.getBinName(), hasIndexesForField);
        }
        return hasIndexesForField;
    }

    private int getMinBinValuesRatioForQualifier(Statement stmt, Qualifier qualifier) {
        // Get all indexes for field
        List<Index> indexList = indexesCache.getAllIndexesForField(
            new IndexedField(stmt.getNamespace(), stmt.getSetName(), qualifier.getBinName()));

        // Return the lowest bin values ratio of the indexes in indexList
        Optional<Index> minBinValuesRatio = indexList.stream()
            .filter(index -> index.getBinValuesRatio() != 0)
            .min(Comparator.comparing(Index::getBinValuesRatio));

        return minBinValuesRatio.map(Index::getBinValuesRatio).orElse(0);
    }
}
