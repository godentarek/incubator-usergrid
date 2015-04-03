/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  *  contributor license agreements.  The ASF licenses this file to You
 *  * under the Apache License, Version 2.0 (the "License"); you may not
 *  * use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.  For additional information regarding
 *  * copyright in this work, please see the NOTICE file in the top level
 *  * directory of this distribution.
 *
 */
package org.apache.usergrid.persistence.index.impl;


import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.usergrid.persistence.core.scope.ApplicationScope;
import org.apache.usergrid.persistence.index.EntityIndex;
import org.apache.usergrid.persistence.index.SearchEdge;
import org.apache.usergrid.persistence.index.SearchTypes;
import org.apache.usergrid.persistence.index.exceptions.IndexException;
import org.apache.usergrid.persistence.index.query.ParsedQuery;
import org.apache.usergrid.persistence.index.query.SortPredicate;
import org.apache.usergrid.persistence.index.query.tree.QueryVisitor;

import com.google.common.base.Preconditions;

import static org.apache.usergrid.persistence.index.impl.IndexingUtils.createContextName;

/**
 * The strategy for creating a search request from a parsed query
 */

public class SearchRequestBuilderStrategy {

    private static final Logger logger = LoggerFactory.getLogger(SearchRequestBuilderStrategy.class);

    private final EsProvider esProvider;
    private final ApplicationScope applicationScope;
    private final IndexAlias alias;
    private final int cursorTimeout;

    public SearchRequestBuilderStrategy(final EsProvider esProvider, final ApplicationScope applicationScope, final IndexAlias alias, int cursorTimeout){

        this.esProvider = esProvider;
        this.applicationScope = applicationScope;
        this.alias = alias;
        this.cursorTimeout = cursorTimeout;
    }

    public SearchRequestBuilder getBuilder(final SearchEdge searchEdge, final SearchTypes searchTypes, final ParsedQuery query,  final int limit) {

        Preconditions.checkArgument(limit <= EntityIndex.MAX_LIMIT, "limit is greater than max "+ EntityIndex.MAX_LIMIT);

        SearchRequestBuilder srb = esProvider.getClient().prepareSearch(alias.getReadAlias())
            .setTypes(searchTypes.getTypeNames(applicationScope))
            .setScroll(cursorTimeout + "m")
            .setQuery(createQueryBuilder( searchEdge,query));

        final FilterBuilder fb = createFilterBuilder(query);

        //we have post filters, apply them
        if (fb != null) {
            logger.debug("   Filter: {} ", fb.toString());
            srb = srb.setPostFilter(fb);
        }


        srb = srb.setFrom(0).setSize(limit);

        for (SortPredicate sp : query.getSortPredicates()) {
            throw new RuntimeException( "Fix me" );

//            final SortOrder order;
//            if (sp.getDirection().equals( SortPredicate.SortDirection.ASCENDING)) {
//                order = SortOrder.ASC;
//            } else {
//                order = SortOrder.DESC;
//            }
//
//            // we do not know the type of the "order by" property and so we do not know what
//            // type prefix to use. So, here we add an order by clause for every possible type
//            // that you can order by: string, number and boolean and we ask ElasticSearch
//            // to ignore any fields that are not present.
//
//            final String stringFieldName = STRING_PREFIX + sp.getPropertyName();
//            final FieldSortBuilder stringSort = SortBuilders.fieldSort(stringFieldName)
//                .order(order).ignoreUnmapped(true);
//            srb.addSort(stringSort);
//
//            logger.debug("   Sort: {} order by {}", stringFieldName, order.toString());
//
//            final String longFieldName = LONG_PREFIX + sp.getPropertyName();
//            final FieldSortBuilder longSort = SortBuilders.fieldSort(longFieldName)
//                .order(order).ignoreUnmapped(true);
//            srb.addSort(longSort);
//            logger.debug("   Sort: {} order by {}", longFieldName, order.toString());
//
//
//            final String doubleFieldName = DOUBLE_PREFIX + sp.getPropertyName();
//            final FieldSortBuilder doubleSort = SortBuilders.fieldSort(doubleFieldName)
//                .order(order).ignoreUnmapped(true);
//            srb.addSort(doubleSort);
//            logger.debug("   Sort: {} order by {}", doubleFieldName, order.toString());
//
//
//            final String booleanFieldName = BOOLEAN_PREFIX + sp.getPropertyName();
//            final FieldSortBuilder booleanSort = SortBuilders.fieldSort(booleanFieldName)
//                .order(order).ignoreUnmapped(true);
//            srb.addSort(booleanSort);
//            logger.debug("   Sort: {} order by {}", booleanFieldName, order.toString());
        }
        return srb;
    }


    public QueryBuilder createQueryBuilder(final  SearchEdge searchEdge, final ParsedQuery query) {
        String context = createContextName(applicationScope, searchEdge );

        QueryBuilder queryBuilder = null;

        //we have a root operand.  Translate our AST into an ES search
        if ( query.getRootOperand() != null ) {
            // In the case of geo only queries, this will return null into the query builder.
            // Once we start using tiles, we won't need this check any longer, since a geo query
            // will return a tile query + post filter
            QueryVisitor v = new EsQueryVistor();

            try {
                query.getRootOperand().visit( v );
            }
            catch ( IndexException ex ) {
                throw new RuntimeException( "Error building ElasticSearch query", ex );
            }


            queryBuilder = v.getQueryBuilder();
        }


        // Add our filter for context to our query for fast execution.
        // Fast because it utilizes bitsets internally. See this post for more detail.
        // http://www.elasticsearch.org/blog/all-about-elasticsearch-filter-bitsets/

        // TODO evaluate performance when it's an all query.
        // Do we need to put the context term first for performance?

        //make sure we have entity in the context
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        boolQueryBuilder.must(  QueryBuilders.termQuery( IndexingUtils.EDGE_SEARCH_FIELDNAME, context ) );

        boolQueryBuilder.must( queryBuilder );

        return queryBuilder;
    }


    public FilterBuilder createFilterBuilder(ParsedQuery query) {
        FilterBuilder filterBuilder = null;

        if ( query.getRootOperand() != null ) {
            QueryVisitor v = new EsQueryVistor();
            try {
                query.getRootOperand().visit( v );

            } catch ( IndexException ex ) {
                throw new RuntimeException( "Error building ElasticSearch query", ex );
            }
            filterBuilder = v.getFilterBuilder();
        }

        return filterBuilder;
    }

}