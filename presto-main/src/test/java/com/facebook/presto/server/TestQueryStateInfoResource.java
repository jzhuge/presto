/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.server;

import com.facebook.presto.client.QueryResults;
import com.facebook.presto.server.testing.TestingPrestoServer;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.client.UnexpectedResponseException;
import io.airlift.http.client.jetty.JettyHttpClient;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static com.facebook.presto.client.PrestoHeaders.PRESTO_USER;
import static com.facebook.presto.execution.QueryState.QUEUED;
import static com.facebook.presto.testing.assertions.Assert.assertEquals;
import static io.airlift.http.client.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.json.JsonCodec.listJsonCodec;
import static io.airlift.testing.Closeables.closeQuietly;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Test(singleThreaded = true)
public class TestQueryStateInfoResource
{
    private TestingPrestoServer server;
    private HttpClient client;
    private QueryResults queryResults;

    TestQueryStateInfoResource()
            throws Exception
    {
        server = new TestingPrestoServer();
        client = new JettyHttpClient();
    }

    @BeforeClass
    public void setup()
    {
        Request request1 = preparePost()
                .setUri(uriBuilderFrom(server.getBaseUrl()).replacePath("/v1/statement").build())
                .setBodyGenerator(createStaticBodyGenerator("show catalogs", UTF_8))
                .setHeader(PRESTO_USER, "user1")
                .build();
        queryResults = client.execute(request1, createJsonResponseHandler(jsonCodec(QueryResults.class)));

        Request request2 = preparePost()
                .setUri(uriBuilderFrom(server.getBaseUrl()).replacePath("/v1/statement").build())
                .setBodyGenerator(createStaticBodyGenerator("show catalogs", UTF_8))
                .setHeader(PRESTO_USER, "user2")
                .build();
        client.execute(request2, createJsonResponseHandler(jsonCodec(QueryResults.class)));

        boolean queued = true;
        while (queued) {
            queued = false;
            List<BasicQueryInfo> queryInfos = client.execute(
                    prepareGet().setUri(uriBuilderFrom(server.getBaseUrl()).replacePath("/v1/query").build()).build(),
                    createJsonResponseHandler(listJsonCodec(BasicQueryInfo.class)));
            assertEquals(queryInfos.size(), 2);
            for (BasicQueryInfo queryInfo : queryInfos) {
                queued = queued || queryInfo.getState() == QUEUED;
            }
        }
    }

    @AfterClass(alwaysRun = true)
    public void teardown()
    {
        closeQuietly(server);
        closeQuietly(client);
    }

    @Test
    public void testGetAllQueryStateInfos()
    {
        List<QueryStateInfo> infos = client.execute(
                prepareGet().setUri(server.resolve("/v1/queryState")).build(),
                createJsonResponseHandler(listJsonCodec(QueryStateInfo.class)));

        assertEquals(infos.size(), 2);
    }

    @Test
    public void testGetQueryStateInfosForUser()
    {
        List<QueryStateInfo> infos = client.execute(
                prepareGet().setUri(server.resolve("/v1/queryState?user=user2")).build(),
                createJsonResponseHandler(listJsonCodec(QueryStateInfo.class)));

        assertEquals(infos.size(), 1);
    }

    @Test
    public void testGetQueryStateInfosForUserNoResult()
    {
        List<QueryStateInfo> infos = client.execute(
                prepareGet().setUri(server.resolve("/v1/queryState?user=user3")).build(),
                createJsonResponseHandler(listJsonCodec(QueryStateInfo.class)));

        assertTrue(infos.isEmpty());
    }

    @Test
    public void testGetQueryStateInfo()
    {
        QueryStateInfo info = client.execute(
                prepareGet().setUri(server.resolve("/v1/queryState/" + queryResults.getId())).build(),
                createJsonResponseHandler(jsonCodec(QueryStateInfo.class)));

        assertNotNull(info);
    }

    @Test(expectedExceptions = {UnexpectedResponseException.class}, expectedExceptionsMessageRegExp = ".*404: Not Found")
    public void testGetQueryStateInfoNo()
    {
        client.execute(
                prepareGet().setUri(server.resolve("/v1/queryState/123")).build(),
                createJsonResponseHandler(jsonCodec(QueryStateInfo.class)));
    }
}
