package com.hiddenswitch.spellsource.util;

import com.hazelcast.core.HazelcastInstance;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class Hazelcast {

	public static HazelcastInstance getHazelcastInstance() {
		return getClusterManager().getHazelcastInstance();
	}

	public static HazelcastClusterManager getClusterManager() {
		return (HazelcastClusterManager) ((VertxInternal) (Vertx.currentContext().owner())).getClusterManager();
	}
}

