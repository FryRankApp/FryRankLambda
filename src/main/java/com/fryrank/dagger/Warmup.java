package com.fryrank.dagger;

/**
 * Optional init-time warmup to ensure expensive (but safe-to-snapshot) object creation happens before SnapStart
 * takes a snapshot.
 *
 * Important: avoid doing real network I/O or establishing long-lived sockets here (e.g., opening DB connections),
 * since restored environments can have stale connections after restore.
 */
public final class Warmup {
    private Warmup() {}

    public static void run(final AppComponent component) {
        // Pure object-graph creation (safe)
        component.gson();
        component.apiGatewayRequestValidator();
        component.reviewValidator();
        component.userMetadataValidator();
        component.deleteReviewRequestValidator();

        // If auth is enabled, prefetch SSM-backed config by constructing the Authorizer graph during init.
        // If auth is disabled, skip to avoid an unnecessary SSM roundtrip during init.
        if (!component.authDisabled()) {
            component.authorizer();
        }

        // Builds domains/DALs (clients are created, but AWS SDK clients don't open sockets until first request).
        component.reviewDomain();
        component.userMetadataDomain();
    }
}
