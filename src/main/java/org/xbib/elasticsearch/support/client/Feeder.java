
package org.xbib.elasticsearch.support.client;

import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.bytes.BytesReference;

/**
 * Minimal API for feed
 */
public interface Feeder {

    Client client();

    /**
     * Index document
     *
     * @param index the index
     * @param type the type
     * @param id the id
     * @param source the source
     * @return this
     */
    Feeder index(String index, String type, String id, BytesReference source);

    /**
     * Index document
     * @param indexRequest the index request
     * @return this
     */
    Feeder index(IndexRequest indexRequest);

    /**
     * Delete document
     *
     * @param index the index
     * @param type the type
     * @param id the id
     * @return this
     */
    Feeder delete(String index, String type, String id);

    Feeder delete(DeleteRequest deleteRequest);


}
