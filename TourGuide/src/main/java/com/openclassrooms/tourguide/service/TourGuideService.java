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

    private final Logger logger = LoggerFactory.getLogger(TourGuideService.class);

    private final GpsUtil gpsUtil;
    private final RewardsService rewardsService;
    private final TripPricer tripPricer = new TripPricer();

    public final Tracker tracker;

    boolean testMode = true;

    public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
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
         * Otherwise, get a new location from GpsUtil.
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
         * Add the user only if the username does not already exist.
         */
        if (!internalUserMap.containsKey(user.getUserName())) {
            internalUserMap.put(user.getUserName(), user);
        }
    }

    public List<Provider> getTripDeals(User user) {

        /*
         * Calculate the total number of reward points.
         */
        int cumulativeRewardPoints = user.getUserRewards()
                .stream()
                .mapToInt(UserReward::getRewardPoints)
                .sum();

        List<Provider> providers = tripPricer.getPrice(
                tripPricerApiKey,
                user.getUserId(),
                user.getUserPreferences().getNumberOfAdults(),
                user.getUserPreferences().getNumberOfChildren(),
                user.getUserPreferences().getTripDuration(),
                cumulativeRewardPoints
        );

        user.setTripDeals(providers);

        return providers;
    }

    public VisitedLocation trackUserLocation(User user) {

        /*
         * Get the current user location from GpsUtil.
         */
        VisitedLocation visitedLocation =
                gpsUtil.getUserLocation(user.getUserId());

        /*
         * Save the new location in the user history.
         */
        user.addToVisitedLocations(visitedLocation);

        /*
         * Calculate possible rewards for this user.
         */
        rewardsService.calculateRewards(user);

        return visitedLocation;
    }

    public List<Attraction> getNearByAttractions(
            VisitedLocation visitedLocation) {

        /*
         * Get all attractions.
         * Sort them from the nearest to the farthest.
         * Keep only the first five attractions.
         *
         * We do not use a maximum distance because the requirement
         * asks for the five nearest attractions, no matter how far away.
         */
        return gpsUtil.getAttractions()
                .stream()
                .sorted(
                        Comparator.comparingDouble(
                                attraction -> rewardsService.getDistance(
                                        visitedLocation.location,
                                        attraction
                                )
                        )
                )
                .limit(5)
                .collect(Collectors.toList());
    }

    private void addShutDownHook() {

        /*
         * Stop the tracker when the application shuts down.
         */
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            tracker.stopTracking();
        }));
    }

    /**************************************************************************
     *
     * Methods below are used for internal testing.
     *
     **************************************************************************/

    private static final String tripPricerApiKey =
            "test-server-api-key";

    /*
     * External users would normally be stored in a database.
     * Internal test users are stored in memory.
     */
    private final Map<String, User> internalUserMap =
            new HashMap<>();

    private void initializeInternalUsers() {

        IntStream.range(
                0,
                InternalTestHelper.getInternalUserNumber()
        ).forEach(i -> {

            String userName = "internalUser" + i;
            String phone = "000";
            String email = userName + "@tourGuide.com";

            User user = new User(
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
         * Create three random locations for each internal test user.
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
        double leftLimit = -180;
        double rightLimit = 180;

        return leftLimit
                + new Random().nextDouble()
                * (rightLimit - leftLimit);
    }

    private double generateRandomLatitude() {
        double leftLimit = -85.05112878;
        double rightLimit = 85.05112878;

        return leftLimit
                + new Random().nextDouble()
                * (rightLimit - leftLimit);
    }

    private Date getRandomTime() {

        LocalDateTime localDateTime = LocalDateTime.now()
                .minusDays(new Random().nextInt(30));

        return Date.from(
                localDateTime.toInstant(ZoneOffset.UTC)
        );
    }
}