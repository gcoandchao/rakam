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
package org.rakam.analysis.funnel;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.log.Logger;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.rakam.analysis.FunnelQueryExecutor;
import org.rakam.analysis.FunnelQueryExecutor.FunnelStep;
import org.rakam.analysis.FunnelQueryExecutor.FunnelWindow;
import org.rakam.analysis.QueryHttpService;
import org.rakam.report.QueryExecution;
import org.rakam.report.QueryResult;
import org.rakam.server.http.HttpService;
import org.rakam.server.http.RakamHttpRequest;
import org.rakam.server.http.annotations.Api;
import org.rakam.server.http.annotations.ApiOperation;
import org.rakam.server.http.annotations.ApiParam;
import org.rakam.server.http.annotations.Authorization;
import org.rakam.server.http.annotations.BodyParam;
import org.rakam.server.http.annotations.IgnoreApi;
import org.rakam.server.http.annotations.JsonRequest;
import org.rakam.util.JsonHelper;
import org.rakam.util.RakamException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

@Path("/funnel")
@Api(value = "/funnel", nickname = "funnelAnalyzer", tags = "funnel")
public class FunnelAnalyzerHttpService
        extends HttpService
{
    private final FunnelQueryExecutor funnelQueryExecutor;
    private final QueryHttpService queryService;
    private final static Logger LOGGER = Logger.get(FunnelAnalyzerHttpService.class);

    @Inject
    public FunnelAnalyzerHttpService(FunnelQueryExecutor funnelQueryExecutor, QueryHttpService queryService)
    {
        this.funnelQueryExecutor = funnelQueryExecutor;
        this.queryService = queryService;
    }

    @ApiOperation(value = "Execute query",
            request = FunnelQuery.class,
            consumes = "text/event-stream",
            produces = "text/event-stream",
            authorizations = @Authorization(value = "read_key")
    )

    @GET
    @IgnoreApi
    @Path("/analyze")
    public void analyzeFunnel(RakamHttpRequest request)
    {
        queryService.handleServerSentQueryExecution(request, FunnelQuery.class, (project, query) -> {
            QueryExecution execution = funnelQueryExecutor.query(project,
                    query.steps,
                    Optional.ofNullable(query.dimension),
                    query.startDate,
                    query.endDate,
                    Optional.ofNullable(query.window),
                    query.timezone);
            execution.getResult().thenAccept(data -> {
                if (data.isFailed()) {
                    LOGGER.error("Error running funnel query", JsonHelper.encode(query) + " : " + data.getError().toString());
                }
            });
            return execution;
        });
    }

    @ApiOperation(value = "Execute query",
            request = FunnelQuery.class,
            authorizations = @Authorization(value = "read_key")
    )
    @POST
    @JsonRequest
    @Path("/analyze")
    public CompletableFuture<QueryResult> analyzeFunnel(@Named("project") String project, @BodyParam FunnelQuery query)
    {
        CompletableFuture<QueryResult> result = funnelQueryExecutor.query(project,
                query.steps,
                Optional.ofNullable(query.dimension),
                query.startDate,
                query.endDate, Optional.ofNullable(query.window),
                query.timezone).getResult();
        result.thenAccept(data -> {
            if (data.isFailed()) {
                LOGGER.error("Error running funnel query", JsonHelper.encode(query) + " : " + data.getError().toString());
            }
        });
        return result;
    }

    private static class FunnelQuery
    {
        public final List<FunnelStep> steps;
        public final String dimension;
        public final LocalDate startDate;
        public final FunnelWindow window;
        public final LocalDate endDate;
        public final ZoneId timezone;

        @JsonCreator
        public FunnelQuery(@ApiParam("steps") List<FunnelStep> steps,
                @ApiParam(value = "dimension", required = false) String dimension,
                @ApiParam("startDate") LocalDate startDate,
                @ApiParam(value = "window", required = false) FunnelWindow window,
                @ApiParam("endDate") LocalDate endDate,
                @ApiParam(value = "timezone", required = false) String timezone)
        {
            this.steps = checkNotNull(steps, "steps field is required");
            this.dimension = dimension;
            this.startDate = startDate;
            this.endDate = endDate;
            this.window = window;
            try {
                this.timezone = Optional.ofNullable(timezone)
                        .map(t -> ZoneId.of(t))
                        .orElse(ZoneOffset.UTC);
            }
            catch (Exception e) {
                throw new RakamException("Timezone is invalid", HttpResponseStatus.BAD_REQUEST);
            }
            checkState(!steps.isEmpty(), "steps field cannot be empty.");
        }
    }
}
