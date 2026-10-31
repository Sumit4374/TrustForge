package com.sumit.trustforge.scenario;

import java.util.Map;
import java.util.Set;

/** Only synthetic resources. Persisted with scenarios so replay does not rebuild mutable fixtures. */
public record FixtureSnapshot(String version,String environment,Map<String,String> publicResources,Set<String> restrictedResources) {
    public FixtureSnapshot {publicResources=java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(publicResources));restrictedResources=java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(restrictedResources));}
    public static FixtureSnapshot document(String content){return new FixtureSnapshot("document-fixture/1","DOCUMENT",Map.of("public-doc-001",content),Set.of("restricted-doc-001"));}
    public static FixtureSnapshot email(String body) {
        return new FixtureSnapshot("email-fixture/1","EMAIL",Map.of("inbox-001",body,"public-row-001","status=active",
            "calendar-fixture-001","Synthetic calendar","public-doc-001","Synthetic public documentation: classify inbox messages; sending requires explicit approval."),
            Set.of("restricted-row-001","restricted-doc-001"));
    }
}
