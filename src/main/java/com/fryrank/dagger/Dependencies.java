package com.fryrank.dagger;

import lombok.extern.log4j.Log4j2;

@Log4j2
public final class Dependencies {
    private static final AppComponent APP_COMPONENT;

    static {
        final long startNanos = System.nanoTime();
        APP_COMPONENT = DaggerAppComponent.create();

        final long createdMillis = (System.nanoTime() - startNanos) / 1_000_000;
        log.info("Dagger graph created in {} ms", createdMillis);

        log.info(
                "Lambda init: AWS_LAMBDA_FUNCTION_VERSION={}, AWS_LAMBDA_INITIALIZATION_TYPE={}",
                APP_COMPONENT.lambdaFunctionVersion(),
                APP_COMPONENT.lambdaInitializationType()
        );

        final long warmupStartNanos = System.nanoTime();
        Warmup.run(APP_COMPONENT);
        final long warmupMillis = (System.nanoTime() - warmupStartNanos) / 1_000_000;
        log.info("Warmup completed in {} ms", warmupMillis);
    }

    private Dependencies() {}

    public static AppComponent appComponent() {
        return APP_COMPONENT;
    }
}
