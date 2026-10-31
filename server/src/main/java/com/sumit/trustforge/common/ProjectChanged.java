package com.sumit.trustforge.common;

import java.util.UUID;

/** Safe invalidation signal; contains no private content. */
public record ProjectChanged(UUID projectId) {}
