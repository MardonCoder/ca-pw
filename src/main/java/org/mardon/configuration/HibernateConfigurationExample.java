package org.mardon.configuration;

import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.jsr107.Eh107Configuration;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.mardon.database.Event;
import org.mardon.database.Ticket;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import java.time.Duration;

public class HibernateConfigurationExample {

    public static SessionFactory buildSessionFactory() {
        // 1. Pre-configure regions in Ehcache 3 (sets heap size and TTL)
        String providerClass = "org.ehcache.jsr107.EhcacheCachingProvider";
        CachingProvider provider = Caching.getCachingProvider(providerClass);
        CacheManager cacheManager = provider.getCacheManager(
                provider.getDefaultURI(),
                HibernateConfigurationExample.class.getClassLoader()
        );

        var regionConfig = Eh107Configuration.fromEhcacheCacheConfiguration(
                CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                Object.class,
                                Object.class,
                                ResourcePoolsBuilder.heap(10_000)
                        )
                        .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(10)))
                        .build()
        );

        // Register regions for entities and the @OneToMany collection
        registerRegion(cacheManager, Event.class.getName(), regionConfig);
        registerRegion(cacheManager, Ticket.class.getName(), regionConfig);
        registerRegion(cacheManager, Event.class.getName() + ".tickets", regionConfig);

        // 2. Hibernate configuration
        Configuration configuration = new Configuration();

        // Database connection (PostgreSQL example)
        configuration.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
        configuration.setProperty("hibernate.connection.url", "jdbc:postgresql://localhost:5432/app_db");
        configuration.setProperty("hibernate.connection.username", "postgres");
        configuration.setProperty("hibernate.connection.password", "postgres");
        configuration.setProperty("hibernate.hbm2ddl.auto", "validate");

        // Enable L2 cache
        configuration.setProperty("hibernate.cache.use_second_level_cache", "true");
        configuration.setProperty("hibernate.cache.use_query_cache", "false");
        configuration.setProperty("hibernate.cache.region.factory_class", "jcache");
        configuration.setProperty("hibernate.javax.cache.provider", providerClass);
        configuration.setProperty("hibernate.javax.cache.uri", provider.getDefaultURI().toString());

        // Prevent on-the-fly creation of regions without TTL if a region name is misspelled
        configuration.setProperty("hibernate.javax.cache.missing_cache_strategy", "fail");

        // Register @Entity classes
        configuration.addAnnotatedClass(Event.class);
        configuration.addAnnotatedClass(Ticket.class);

        // 3. Build SessionFactory
        return configuration.buildSessionFactory();
    }

    private static void registerRegion(
            CacheManager cacheManager,
            String regionName,
            javax.cache.configuration.Configuration<Object, Object> config
    ) {
        if (cacheManager.getCache(regionName) == null) {
            cacheManager.createCache(regionName, config);
        }
    }
}