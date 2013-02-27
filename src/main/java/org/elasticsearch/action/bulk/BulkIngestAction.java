/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.action.bulk;

import org.elasticsearch.action.Action;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.transport.TransportRequestOptions;

/**
 * Ingest action
 */
public class BulkIngestAction extends Action<BulkIngestRequest, BulkResponse, BulkIngestRequestBuilder> {

    public static final BulkIngestAction INSTANCE = new BulkIngestAction();
    public static final String NAME = "bulkingest";

    private BulkIngestAction() {
        super(NAME);
    }

    @Override
    public BulkResponse newResponse() {
        return new BulkResponse(null, 0L);
    }

    @Override
    public BulkIngestRequestBuilder newRequestBuilder(Client client) {
        return new BulkIngestRequestBuilder(client);
    }

    @Override
    public TransportRequestOptions transportOptions(Settings settings) {
        return TransportRequestOptions.options()
                .withType(TransportRequestOptions.Type.fromString(settings.get("action.bulkingest.transport.type", TransportRequestOptions.Type.LOW.toString())))
                .withCompress(settings.getAsBoolean("action.bulkingest.compress", true));
    }
}
