// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.mydomain.example;

import com.yahoo.application.Application;
import com.yahoo.application.Networking;
import com.yahoo.application.container.Search;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.testutil.DocumentSourceSearcher;
import com.yahoo.search.yql.MinimalQueryInserter;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.util.Arrays;
import java.util.Iterator;

import static java.net.URLEncoder.encode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * Unit tests - demonstrates:
 * <ol>
 *     <li>Build queries from YQL</li>
 *     <li>How to get the query tree, and evaluate it</li>
 *     <li>Use of tracing</li>
 *     <li>using a mock backend for hits</li>
 *     <li>Use of Application for setting up a full environment (chains) / how to build chains</li>
 *     <li>Simple use of config injection</li>
 * </ol>
 */
public class MetalSearcherTest {

    private Query metalQuery;

    /**
     *
     */
    @Before
    public void initQuery() {
        metalQuery = new Query("/search/?yql=" +
                encode("select * from sources * where artist contains \"hetfield\" and title contains\"master of puppets\";",
                        StandardCharsets.UTF_8));
        metalQuery.setTraceLevel(6);
    }


    @Test
    public void testAddedOrTerm1() {

        MetalNamesConfig.Builder builder = new MetalNamesConfig.Builder();
        builder.metalWords(Arrays.asList("hetfield", "metallica", "pantera"));
        MetalNamesConfig config = new MetalNamesConfig(builder);

        Chain<Searcher> myChain = new Chain<>(new MinimalQueryInserter(), new MetalSearcher(config));  // added to chain in this order
        Execution.Context context = Execution.Context.createContextStub();
        Execution execution = new Execution(myChain, context);

        Result result = execution.search(metalQuery);
        System.out.println(result.getContext(false).getTrace());

        assertAddedOrTerm(metalQuery.getModel().getQueryTree().getRoot());
    }


    @Test
    public void testAddedOrTerm2() {

        try (Application app = Application.fromApplicationPackage(
                FileSystems.getDefault().getPath("src/main/application"),
                Networking.disable)) {
            Search search = app.getJDisc("default").search();
            Result result = search.process(ComponentSpecification.fromString("metalchain"), metalQuery);
            System.out.println(result.getContext(false).getTrace());

            assertAddedOrTerm(metalQuery.getModel().getQueryTree().getRoot());
        }
    }


    @Test
    public void testWithMockBackendProducingHits() {

        DocumentSourceSearcher docSource = new DocumentSourceSearcher();
        Query testQuery = new Query();
        testQuery.setTraceLevel(6);
        testQuery.getModel().getQueryTree().setRoot(new WordItem("drum","title"));

        Result mockResult = new Result(testQuery);
        mockResult.hits().add(new Hit("hit:1", 0.9));
        mockResult.hits().add(new Hit("hit:2", 0.8));
        docSource.addResult(testQuery, mockResult);

        Chain<Searcher> myChain = new Chain<>(new MetalSearcher(), docSource);  // no config to MetalSearcher
        Execution.Context context = Execution.Context.createContextStub();
        Execution execution = new Execution(myChain, context);

        Result result = execution.search(testQuery);
        System.out.println(result.getContext(false).getTrace());

        assertEquals("Document source hits are returned",2, result.hits().size());
    }



    // ToDo: From the older versions of this app, a feature and test to demonstrate how to add hits
    // process the result (add a synthetic hit)
    // result.hits().add(new Hit("test:hit", 1.0));
    //    public void testSearcherOnly() {
    //        Result result = newExecution(new MetalSearcher()).search(new Query());
    //        assertEquals("Artificial hit is added", "test:hit", result.hits().get(0).getId().toString());
    //    }


    private void assertAddedOrTerm(Item root) {
        // Assert that an OR term is added to the root, with album:metal as one of the or-terms:
        assertTrue(root instanceof OrItem);
        for (Iterator<Item> iter = ((CompositeItem)root).getItemIterator(); iter.hasNext(); ) {
            Item item = iter.next();
            if (item instanceof WordItem) {
                assertEquals(item.toString(), "album:metal");
            }
        }
    }
}
