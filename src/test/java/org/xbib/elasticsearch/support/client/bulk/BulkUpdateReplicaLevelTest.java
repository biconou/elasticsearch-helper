
package org.xbib.elasticsearch.support.client.bulk;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.unit.TimeValue;
import org.junit.Test;
import org.xbib.elasticsearch.support.helper.AbstractNodeRandomTestHelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class BulkUpdateReplicaLevelTest extends AbstractNodeRandomTestHelper {

    private final static ESLogger logger = ESLoggerFactory.getLogger(BulkUpdateReplicaLevelTest.class.getSimpleName());

    @Test
    public void testUpdateReplicaLevel() throws Exception {

        int numberOfShards = 2;
        int replicaLevel = 3;

        // we need 3 nodes for replica level 3
        startNode("2");
        startNode("3");

        int shardsAfterReplica;

        final BulkTransportClient ingest = new BulkTransportClient()
                .newClient(getAddress())
                .shards(numberOfShards)
                .replica(0)
                .newIndex("replicatest");

        ingest.waitForCluster(ClusterHealthStatus.GREEN, TimeValue.timeValueSeconds(30));

        try {
            for (int i = 0; i < 12345; i++) {
                ingest.index("replicatest", "replicatest", null, "{ \"name\" : \"" + randomString(32) + "\"}");
            }
            ingest.flushIngest();
            ingest.waitForResponses(TimeValue.timeValueSeconds(30));
            shardsAfterReplica = ingest.updateReplicaLevel("replicatest", replicaLevel);
            assertEquals(shardsAfterReplica, numberOfShards * (replicaLevel + 1));
        } catch (NoNodeAvailableException e) {
            logger.warn("skipping, no node available");
        } finally {
            ingest.deleteIndex("replicatest");
            ingest.shutdown();
            if (ingest.hasThrowable()) {
                logger.error("error", ingest.getThrowable());
            }
            assertFalse(ingest.hasThrowable());
        }

        stopNode("3");
        stopNode("2");
    }

}
