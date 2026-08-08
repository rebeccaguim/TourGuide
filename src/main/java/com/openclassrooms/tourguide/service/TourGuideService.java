package com.openclassrooms.tourguide.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.NearbyAttraction;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {

    private static final Logger logger =
            LoggerFactory.getLogger(TourGuideService.class);

    /*
     * Maximum number of users processed at the same time
     * during a location tracking operation.
     */
    private static final int LOCATION_THREAD_POOL_SIZE = 100;

    /*
     * Maximum number of asynchronous tasks created
     * at the same time.
     */
    private static final int LOCATION_BATCH_SIZE = 1_000;

    private final GpsUtil gpsUtil;
    private final RewardsService rewardsService;

    private final TripPricer tripPricer =
            new TripPricer();

    /*
     * The tracker can be null during performance tests
     * when background tracking is disabled.
     */
    public final Tracker tracker;

    boolean testMode = true;

    /*
     * Constructor used by Spring Boot and normal unit tests.
     *
     * The background tracker starts automatically.
     */
    @Autowired
    public TourGuideService(
            GpsUtil gpsUtil,
            RewardsService rewardsService) {

        this(
                gpsUtil,
                rewardsService,
                true
        );
    }

    /*
     * Constructor used when the background tracker
     * must be enabled or disabled.
     *
     * Performance tests use false to avoid processing
     * the same users twice.
     */
    public TourGuideService(
            GpsUtil gpsUtil,
            RewardsService rewardsService,
            boolean startTracker) {

        this.gpsUtil = gpsUtil;
        this.rewardsService = rewardsService;

        Locale.setDefault(Locale.US);

        if (testMode) {
            logger.info("TestMode enabled");
            logger.debug("Initializing users");

            initializeInternalUsers();

            logger.debug("Finished initializing users");
        }

        if (startTracker) {
            tracker = new Tracker(this);
            addShutDownHook();
        } else {
            tracker = null;
        }
    }

    public List<UserReward> getUserRewards(User user) {
        return user.getUserRewards();
    }

    public VisitedLocation getUserLocation(User user) {

        /*
         * Return the last known location when it exists.
         * Otherwise, request a new location from GpsUtil.
         */
        if (!user.getVisitedLocations().isEmpty()) {
            return user.getLastVisitedLocation();
        }

        return trackUserLocation(user);
    }

    public User getUser(String userName) {
        return internalUserMap.get(userName);
    }

    public List<User> getAllUsers() {
        return internalUserMap.values()
                .stream()
                .collect(Collectors.toList());
    }

    public void addUser(User user) {

        /*
         * Do not replace an existing user
         * with the same username.
         */
        if (!internalUserMap.containsKey(
                user.getUserName())) {

            internalUserMap.put(
                    user.getUserName(),
                    user
            );
        }
    }

    public List<Provider> getTripDeals(User user) {

        int cumulativeRewardPoints =
                user.getUserRewards()
                        .stream()
                        .mapToInt(
                                UserReward::getRewardPoints
                        )
                        .sum();

        List<Provider> providers =
                tripPricer.getPrice(
                        tripPricerApiKey,
                        user.getUserId(),
                        user.getUserPreferences()
                                .getNumberOfAdults(),
                        user.getUserPreferences()
                                .getNumberOfChildren(),
                        user.getUserPreferences()
                                .getTripDuration(),
                        cumulativeRewardPoints
                );

        user.setTripDeals(providers);

        return providers;
    }

    /*
     * Tracks the location of one user.
     */
    public VisitedLocation trackUserLocation(User user) {

        /*
         * Request a new user location from GpsUtil.
         */
        VisitedLocation visitedLocation =
                gpsUtil.getUserLocation(
                        user.getUserId()
                );

        /*
         * Save the new location in the user history.
         */
        user.addToVisitedLocations(
                visitedLocation
        );

        /*
         * Calculate rewards after updating
         * the user's location.
         */
        rewardsService.calculateRewards(user);

        return visitedLocation;
    }

    /*
     * Tracks several users concurrently.
     *
     * The multithreading is implemented in the service,
     * not inside the performance test.
     */
    public void trackAllUsersLocations(
            List<User> users) {

        if (users == null || users.isEmpty()) {
            return;
        }

        ExecutorService executorService =
                Executors.newFixedThreadPool(
                        LOCATION_THREAD_POOL_SIZE
                );

        try {

            /*
             * Process the users one batch at a time.
             *
             * This avoids creating 100,000 futures
             * in memory at the same time.
             */
            for (int startIndex = 0;
                    startIndex < users.size();
                    startIndex += LOCATION_BATCH_SIZE) {

                int endIndex = Math.min(
                        startIndex + LOCATION_BATCH_SIZE,
                        users.size()
                );

                List<User> currentBatch =
                        users.subList(
                                startIndex,
                                endIndex
                        );

                CompletableFuture<?>[] futures =
                        currentBatch.stream()
                                .map(user ->
                                        CompletableFuture.runAsync(
                                                () ->
                                                        trackUserLocation(
                                                                user
                                                        ),
                                                executorService
                                        )
                                )
                                .toArray(
                                        CompletableFuture[]::new
                                );

                /*
                 * Wait for the current batch
                 * before starting the next one.
                 */
                CompletableFuture
                        .allOf(futures)
                        .join();
            }

        } finally {

            /*
             * Stop the thread pool after all users
             * have been processed.
             */
            executorService.shutdown();
        }
    }

    /*
     * Returns the five closest attractions,
     * regardless of their distance.
     */
    public List<Attraction> getNearByAttractions(
            VisitedLocation visitedLocation) {

        return gpsUtil.getAttractions()
                .stream()
                .sorted(
                        Comparator.comparingDouble(
                                attraction ->
                                        rewardsService.getDistance(
                                                visitedLocation.location,
                                                attraction
                                        )
                        )
                )
                .limit(5)
                .collect(Collectors.toList());
    }

    /*
     * Builds the complete response required
     * by the nearby-attractions endpoint.
     */
    public List<NearbyAttraction>
            getNearbyAttractionsDetails(
                    VisitedLocation visitedLocation) {

        /*
         * First, find the five nearest attractions.
         */
        List<Attraction> nearestAttractions =
                getNearByAttractions(
                        visitedLocation
                );

        /*
         * Convert each Attraction into
         * a complete response object.
         */
        return nearestAttractions
                .stream()
                .map(attraction -> {

                    double distanceInMiles =
                            rewardsService.getDistance(
                                    visitedLocation.location,
                                    attraction
                            );

                    int rewardPoints =
                            rewardsService
                                    .getAttractionRewardPoints(
                                            attraction,
                                            visitedLocation.userId
                                    );

                    return new NearbyAttraction(
                            attraction.attractionName,
                            attraction.latitude,
                            attraction.longitude,
                            visitedLocation.location.latitude,
                            visitedLocation.location.longitude,
                            distanceInMiles,
                            rewardPoints
                    );
                })
                .collect(Collectors.toList());
    }

    private void addShutDownHook() {

        /*
         * Stop the background tracker
         * when the application shuts down.
         */
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {

                    if (tracker != null) {
                        tracker.stopTracking();
                    }
                })
        );
    }

    /**********************************************************************
     *
     * Methods below are used for internal testing.
     *
     **********************************************************************/

    private static final String tripPricerApiKey =
            "test-server-api-key";

    /*
     * Internal users are stored in memory.
     */
    private final Map<String, User> internalUserMap =
            new HashMap<>();

    private void initializeInternalUsers() {

        IntStream.range(
                0,
                InternalTestHelper
                        .getInternalUserNumber()
        ).forEach(i -> {

            String userName =
                    "internalUser" + i;

            String phone =
                    "000";

            String email =
                    userName + "@tourGuide.com";

            User user =
                    new User(
                            UUID.randomUUID(),
                            userName,
                            phone,
                            email
                    );

            generateUserLocationHistory(user);

            internalUserMap.put(
                    userName,
                    user
            );
        });

        logger.debug(
                "Created "
                        + InternalTestHelper
                                .getInternalUserNumber()
                        + " internal test users."
        );
    }

    private void generateUserLocationHistory(
            User user) {

        /*
         * Create three random visited locations
         * for every internal test user.
         */
        IntStream.range(0, 3)
                .forEach(i -> {

                    VisitedLocation visitedLocation =
                            new VisitedLocation(
                                    user.getUserId(),
                                    new Location(
                                            generateRandomLatitude(),
                                            generateRandomLongitude()
                                    ),
                                    getRandomTime()
                            );

                    user.addToVisitedLocations(
                            visitedLocation
                    );
                });
    }

    private double generateRandomLongitude() {

        double minimumLongitude = -180;
        double maximumLongitude = 180;

        return minimumLongitude
                + new Random().nextDouble()
                * (maximumLongitude
                - minimumLongitude);
    }

    private double generateRandomLatitude() {

        double minimumLatitude =
                -85.05112878;

        double maximumLatitude =
                85.05112878;

        return minimumLatitude
                + new Random().nextDouble()
                * (maximumLatitude
                - minimumLatitude);
    }

    private Date getRandomTime() {

        LocalDateTime localDateTime =
                LocalDateTime.now()
                        .minusDays(
                                new Random()
                                        .nextInt(30)
                        );

        return Date.from(
                localDateTime.toInstant(
                        ZoneOffset.UTC
                )
        );
    }
}