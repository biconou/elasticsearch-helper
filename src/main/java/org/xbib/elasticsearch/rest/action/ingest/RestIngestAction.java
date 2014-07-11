package org.xbib.elasticsearch.rest.action.ingest;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentBuilderString;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.xbib.elasticsearch.action.ingest.IngestActionFailure;
import org.xbib.elasticsearch.action.ingest.IngestProcessor;
import org.xbib.elasticsearch.action.ingest.IngestRequest;
import org.xbib.elasticsearch.action.ingest.IngestResponse;
import org.xbib.elasticsearch.rest.action.support.XContentRestResponse;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestRequest.Method.PUT;
import static org.elasticsearch.rest.RestStatus.BAD_REQUEST;
import static org.elasticsearch.rest.RestStatus.OK;
import static org.xbib.elasticsearch.rest.action.support.RestXContentBuilder.restContentBuilder;

/**
 * <pre>
 * { "index" : { "_index" : "test", "_type" : "type1", "_id" : "1" }
 * { "type1" : { "field1" : "value1" } }
 * { "delete" : { "_index" : "test", "_type" : "type1", "_id" : "2" } }
 * { "create" : { "_index" : "test", "_type" : "type1", "_id" : "1" }
 * { "type1" : { "field1" : "value1" } }
 * </pre>
 */

public class RestIngestAction extends BaseRestHandler {

    private final static ESLogger logger = Loggers.getLogger(RestIngestAction.class);
    /**
     * Count the volume
     */
    public final static AtomicLong volumeCounter = new AtomicLong();
    /**
     * The IngestProcessor
     */
    private final IngestProcessor ingestProcessor;

    @Inject
    public RestIngestAction(Settings settings, Client client, RestController controller) {
        super(settings, client);

        controller.registerHandler(POST, "/_ingest", this);
        controller.registerHandler(PUT, "/_ingest", this);
        controller.registerHandler(POST, "/{index}/_ingest", this);
        controller.registerHandler(PUT, "/{index}/_ingest", this);
        controller.registerHandler(POST, "/{index}/{type}/_ingest", this);
        controller.registerHandler(PUT, "/{index}/{type}/_ingest", this);

        int actions = settings.getAsInt("action.ingest.maxactions", 1000);
        int concurrency = settings.getAsInt("action.ingest.maxconcurrency", Runtime.getRuntime().availableProcessors() * 4);
        ByteSizeValue volume = settings.getAsBytesSize("action.ingest.maxvolume", ByteSizeValue.parseBytesSizeValue("10m"));
        TimeValue waitingTime = settings.getAsTime("action.ingest.waitingtime", TimeValue.timeValueSeconds(60));

        this.ingestProcessor = new IngestProcessor(client)
                .maxActions(actions)
                .maxConcurrentRequests(concurrency)
                .maxVolumePerRequest(volume)
                .maxWaitForResponses(waitingTime);
    }

    @Override
    public void handleRequest(final RestRequest request, final RestChannel channel) {

        final IngestIdHolder idHolder = new IngestIdHolder();
        final CountDownLatch latch = new CountDownLatch(1);

        IngestProcessor.IngestListener ingestListener = new IngestProcessor.IngestListener() {
            @Override
            public void onRequest(int concurrency, IngestRequest ingestRequest) {
                long v = volumeCounter.addAndGet(ingestRequest.estimatedSizeInBytes());
                if (logger.isDebugEnabled()) {
                    logger.debug("ingest request [{}] of {} items, {} bytes, {} concurrent requests",
                            ingestRequest.ingestId(), ingestRequest.numberOfActions(), v, concurrency);
                }
                idHolder.ingestId(ingestRequest.ingestId());
                latch.countDown();
            }

            @Override
            public void onResponse(int concurrency, IngestResponse response) {
                if (logger.isDebugEnabled()) {
                    logger.debug("ingest response [{}] [{} succeeded] [{} failed] [{}ms]",
                            response.ingestId(),
                            response.successSize(),
                            response.getFailures().size(),
                            response.tookInMillis());
                }
                if (!response.getFailures().isEmpty()) {
                    for (IngestActionFailure f : response.getFailures()) {
                        logger.error("ingest [{}] failure, reason: {}", response.ingestId(), f.message());
                    }
                }
            }

            @Override
            public void onFailure(int concurrency, long ingestId, Throwable failure) {
                logger.error("ingest [{}] error", ingestId, failure);
            }
        };
        try {
            long t0 = System.currentTimeMillis();
            ingestProcessor.add(request.content(),
                    request.contentUnsafe(),
                    request.param("index"),
                    request.param("type"),
                    ingestListener);
            // estimation, should be enough time to wait for an ID
            boolean b = latch.await(100, TimeUnit.MILLISECONDS);
            long t1 = System.currentTimeMillis();

            XContentBuilder builder = restContentBuilder(request);
            builder.startObject();
            builder.field(Fields.TOOK, t1 - t0);
            // got ID?
            if (b) {
                builder.field(Fields.ID, idHolder.ingestId());
            }
            builder.endObject();
            channel.sendResponse(new XContentRestResponse(request, OK, builder));
        } catch (Exception e) {
            try {
                XContentBuilder builder = restContentBuilder(request);
                channel.sendResponse(new XContentRestResponse(request, BAD_REQUEST, builder.startObject().field("error", e.getMessage()).endObject()));
            } catch (IOException e1) {
                logger.error("Failed to send failure response", e1);
            }
        }
    }

    static final class Fields {
        static final XContentBuilderString TOOK = new XContentBuilderString("took");
        static final XContentBuilderString ID = new XContentBuilderString("id");
    }

    static final class IngestIdHolder {
        private long ingestId;

        public void ingestId(long ingestId) {
            this.ingestId = ingestId;
        }

        public long ingestId() {
            return ingestId;
        }
    }

}
