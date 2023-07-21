/*
 * Copyright Kroxylicious Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.kroxylicious.proxy.internal.filter;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.apache.kafka.common.message.ApiVersionsRequestData;
import org.apache.kafka.common.message.MetadataRequestData;
import org.apache.kafka.common.message.MetadataResponseData;
import org.apache.kafka.common.message.ProduceRequestData;
import org.apache.kafka.common.message.RequestHeaderData;
import org.apache.kafka.common.message.SaslAuthenticateRequestData;
import org.apache.kafka.common.message.SaslHandshakeRequestData;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.protocol.ApiMessage;
import org.apache.kafka.common.requests.AbstractRequest;
import org.apache.kafka.common.requests.ApiVersionsRequest;
import org.apache.kafka.common.requests.MetadataRequest;
import org.apache.kafka.common.requests.ProduceRequest;
import org.apache.kafka.common.requests.SaslAuthenticateRequest;
import org.apache.kafka.common.requests.SaslHandshakeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.kroxylicious.proxy.filter.KrpcFilterContext;

import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EagerMetadataLearnerTest {

    @Mock
    KrpcFilterContext context;
    private EagerMetadataLearner learner;

    @BeforeEach
    void setUp() {
        learner = new EagerMetadataLearner();
    }

    public static Stream<Arguments> preludeRequests() {
        return Stream.of(
                toArgs(new ApiVersionsRequest(new ApiVersionsRequestData(), ApiVersionsRequestData.HIGHEST_SUPPORTED_VERSION)),
                toArgs(new SaslHandshakeRequest(new SaslHandshakeRequestData(), SaslHandshakeRequestData.HIGHEST_SUPPORTED_VERSION)),
                toArgs(new SaslAuthenticateRequest(new SaslAuthenticateRequestData(), SaslHandshakeRequestData.HIGHEST_SUPPORTED_VERSION)));
    }

    @ParameterizedTest
    @MethodSource("preludeRequests")
    public void forwardsRequestsOfKafkaPrelude(ApiKeys apiKey, RequestHeaderData header, ApiMessage request) {
        learner.onRequest(apiKey, header, request, context);
        verify(context).forwardRequest(header, request);
    }

    public static Stream<Arguments> postPreludeRequests() {
        return Stream.of(
                toArgs(new ProduceRequest(new ProduceRequestData(), ProduceRequestData.HIGHEST_SUPPORTED_VERSION)),
                toArgs(new MetadataRequest(new MetadataRequestData(), MetadataRequestData.HIGHEST_SUPPORTED_VERSION)),
                toArgs(new MetadataRequest(new MetadataRequestData(), MetadataRequestData.LOWEST_SUPPORTED_VERSION)));
    }

    @ParameterizedTest
    @MethodSource("postPreludeRequests")
    public void spontaneouslyEmitsMetadataRequest(ApiKeys apiKey, RequestHeaderData header, ApiMessage request) {
        var metadataResponse = new MetadataResponseData();
        metadataResponse.brokers().add(new MetadataResponseData.MetadataResponseBroker().setNodeId(1).setHost("localhost").setPort(1234));

        when(context.sendRequest(anyShort(), isA(MetadataRequestData.class))).thenReturn(CompletableFuture.completedStage(metadataResponse));
        learner.onRequest(apiKey, header, request, context);

        verify(context, apiKey.equals(ApiKeys.METADATA) ? times(1) : never()).forwardResponse(metadataResponse);
        verify(context).closeConnection();
    }

    private static Arguments toArgs(AbstractRequest request) {
        var header = new RequestHeaderData().setRequestApiKey(request.apiKey().id).setRequestApiVersion(request.version());
        var apiKey = request.apiKey();
        var request1 = request.data();
        return Arguments.of(apiKey, header, request1);
    }

}