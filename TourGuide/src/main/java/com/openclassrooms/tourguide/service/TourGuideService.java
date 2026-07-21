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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final Logger logger =
            LoggerFactory.getLogger(TourGuideService.class);

    private final GpsUtil gpsUtil;
    private final RewardsService rewardsService;

    private final TripPricer tripPricer =
            new TripPricer();

    public final Tracker tracker;

    boolean testMode = true;

    public TourGuideService(
            GpsUtil gpsUtil,
            RewardsService rewardsService) {

        this.gpsUtil = gpsUtil;
        this.rewardsService = rewardsService;

        Locale.setDefault(Locale.US);

        if (testMode) {
            logger.info("TestMode enabled");
            logger.debug("Initializing users");

            initializeInternalUsers();

            logger.debug("Finished initializing users");
        }

        tracker = new Tracker(this);
        addShutDownHook();
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
        if (!internalUserMap.containsKey(user.getUserName())) {
            internalUserMap.put(user.getUserName(), user);
        }
    }

    public List<Provider> getTripDeals(User user) {

        int cumulativeRewardPoints =
                user.getUserRewards()
                        .stream()
                        .mapToInt(reward ->
                                reward.getRewardPoints())
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

    public VisitedLocation trackUserLocation(User user) {

        /*
         * Request a new user location.
         */
        VisitedLocation visitedLocation =
                gpsUtil.getUserLocation(user.getUserId());

        /*
         * Save the new location in the user history.
         */
        user.addToVisitedLocations(visitedLocation);

        /*
         * Calculate the user's possible rewards.
         */
        rewardsService.calculateRewards(user);

        return visitedLocation;
    }

    /*
     * This method is kept because the existing unit test
     * expects a List of Attraction objects.
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
     * Build the complete response required
     * by the getNearbyAttractions endpoint.
     */
    public List<NearbyAttraction> getNearbyAttractionsDetails(
            VisitedLocation visitedLocation) {

        /*
         * First, find the five nearest attractions.
         */
        List<Attraction> nearestAttractions =
                getNearByAttractions(visitedLocation);

        /*
         * Then, convert every Attraction into
         * a complete NearbyAttraction response object.
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
                            rewardsService.getAttractionRewardPoints(
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
                new Thread(() ->
                        tracker.stopTracking())
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
     * Internal test users are stored in memory.
     */
    private final Map<String, User> internalUserMap =
            new HashMap<>();

    private void initializeInternalUsers() {

        IntStream.range(
                0,
                InternalTestHelper.getInternalUserNumber()
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

            internalUserMap.put(userName, user);
        });

        logger.debug(
                "Created "
                        + InternalTestHelper.getInternalUserNumber()
                        + " internal test users."
        );
    }

    private void generateUserLocationHistory(User user) {

        /*
         * Create three random visited locations
         * for every internal test user.
         */
        IntStream.range(0, 3).forEach(i -> {

            VisitedLocation visitedLocation =
                    new VisitedLocation(
                            user.getUserId(),
                            new Location(
                                    generateRandomLatitude(),
                                    generateRandomLongitude()
                            ),
                            getRandomTime()
                    );

            user.addToVisitedLocations(visitedLocation);
        });
    }

    private double generateRandomLongitude() {

        double minimumLongitude = -180;
        double maximumLongitude = 180;

        return minimumLongitude
                + new Random().nextDouble()
                * (maximumLongitude - minimumLongitude);
    }

    private double generateRandomLatitude() {

        double minimumLatitude = -85.05112878;
        double maximumLatitude = 85.05112878;

        return minimumLatitude
                + new Random().nextDouble()
                * (maximumLatitude - minimumLatitude);
    }

    private Date getRandomTime() {

        LocalDateTime localDateTime =
                LocalDateTime.now()
                        .minusDays(
                                new Random().nextInt(30)
                        );

        return Date.from(
                localDateTime.toInstant(ZoneOffset.UTC)
        );
    }
}