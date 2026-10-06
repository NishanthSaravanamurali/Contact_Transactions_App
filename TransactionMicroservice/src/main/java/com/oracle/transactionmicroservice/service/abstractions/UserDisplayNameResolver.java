package com.oracle.transactionmicroservice.service.abstractions;

import java.util.Map;
import java.util.Set;

public interface UserDisplayNameResolver {
    Map<Long, String> resolve(Set<Long> userIds);
}
