/*
 * Copyright Kroxylicious Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.kroxylicious.kubernetes.operator;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.Operator;

import io.kroxylicious.kubernetes.operator.config.FilterApiDecl;
import io.kroxylicious.kubernetes.operator.config.RuntimeDecl;

/**
 * The {@code main} method entrypoint for the operator
 */
public class OperatorMain {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperatorMain.class);

    public static void main(String[] args) {
        // TODO read these from some configuration CR
        var runtimeDecl = new RuntimeDecl(List.of(
                new FilterApiDecl("filter.kroxylicious.io", "v1alpha1", "RecordEncryption", "io.kroxylicious.filter.encryption.RecordEncryption"),
                new FilterApiDecl("filter.kroxylicious.io", "v1alpha1", "RecordValidation", "io.kroxylicious.filter.validation.RecordValidation")));
        Operator operator = new Operator();
        operator.installShutdownHook(Duration.ofSeconds(10));
        try {
            var registeredController = operator.register(new ProxyReconciler(runtimeDecl));
            // TODO couple the health of the registeredController to the operator's HTTP healthchecks
            operator.start();
            LOGGER.info("Operator started.");
        }
        catch (Exception e) {
            LOGGER.error("Operator has thrown exception during startup. Will now exit.", e);
            System.exit(1);
        }
    }
}