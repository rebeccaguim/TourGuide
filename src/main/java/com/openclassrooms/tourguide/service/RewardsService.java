package com.openclassrooms.tourguide.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Service;

import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;

@Service
public class RewardsService {

    private static final double STATUTE_MILES_PER_NAUTICAL_MILE =
            1.15077945;

    /*
     * Maximum distance used to decide whether a user
     * actually visited an attraction.
     */
    private static final int DEFAULT_PROXIMITY_BUFFER = 10;

    /*
     * Maximum distance used by the application
     * to identify nearby attractions.
     */
    private static final int ATTRACTION_PROXIMITY_RANGE = 200;

    /*
     * Number of users that can be processed simultaneously.
     *
     * RewardCentral is a slow external service.
     * Several threads allow the application to wait for
     * several RewardCentral responses at the same time.
     */
    private static final int REWARD_THREAD_POOL_SIZE = 100;

    private int proximityBuffer = DEFAULT_PROXIMITY_BUFFER;

    private final RewardCentral rewardsCentral;

    /*
     * Attractions are loaded only once.
     *
     * The original implementation called
     * gpsUtil.getAttractions() for every user.
     * The attraction list does not change during execution,
     * so it can safely be reused.
     */
    private final List<Attraction> attractions;

    public RewardsService(
            GpsUtil gpsUtil,
            RewardCentral rewardsCentral) {

        this.rewardsCentral = rewardsCentral;
        this.attractions = List.copyOf(
                gpsUtil.getAttractions()
        );
    }

    public void setProximityBuffer(int proximityBuffer) {
        this.proximityBuffer = proximityBuffer;
    }

    public void setDefaultProximityBuffer() {
        proximityBuffer = DEFAULT_PROXIMITY_BUFFER;
    }

    /*
     * Calculates the rewards for one user.
     *
     * This method remains available because the unit tests
     * and the rest of the application use it.
     */
    public void calculateRewards(User user) {

        List<VisitedLocation> userLocations =
                user.getVisitedLocations();

        /*
         * Store the IDs of attractions that already have
         * a reward.
         *
         * A HashSet can usually find an ID much faster than
         * repeatedly scanning the complete rewards list.
         */
        Set<UUID> rewardedAttractionIds = new HashSet<>();

        for (UserReward userReward : user.getUserRewards()) {
            rewardedAttractionIds.add(
                    userReward.attraction.attractionId
            );
        }

        /*
         * Check each attraction only until a matching
         * visited location is found.
         */
        for (Attraction attraction : attractions) {

            if (rewardedAttractionIds.contains(
                    attraction.attractionId
            )) {
                continue;
            }

            VisitedLocation matchingLocation =
                    findMatchingLocation(
                            userLocations,
                            attraction
                    );

            if (matchingLocation != null) {

                int rewardPoints =
                        getAttractionRewardPoints(
                                attraction,
                                user.getUserId()
                        );

                UserReward userReward =
                        new UserReward(
                                matchingLocation,
                                attraction,
                                rewardPoints
                        );

                user.addUserReward(userReward);

                /*
                 * Update the set immediately to prevent
                 * duplicate rewards during this calculation.
                 */
                rewardedAttractionIds.add(
                        attraction.attractionId
                );
            }
        }
    }

    /*
     * Calculates rewards for several users concurrently.
     *
     * CompletableFuture represents a task that runs
     * asynchronously.
     *
     * allOf(...).join() waits until every user has been
     * completely processed before this method returns.
     */
    public void calculateRewards(List<User> users) {

        if (users == null || users.isEmpty()) {
            return;
        }

        ExecutorService executorService =
                Executors.newFixedThreadPool(
                        REWARD_THREAD_POOL_SIZE
                );

        try {
            CompletableFuture<?>[] futures =
                    users.stream()
                            .map(user ->
                                    CompletableFuture.runAsync(
                                            () -> calculateRewards(user),
                                            executorService
                                    )
                            )
                            .toArray(
                                    CompletableFuture[]::new
                            );

            /*
             * Wait for every asynchronous calculation.
             *
             * This is important because the performance test
             * checks the user rewards immediately afterwards.
             */
            CompletableFuture.allOf(futures).join();

        } finally {
            /*
             * Stop the thread pool after the complete batch.
             *
             * Without shutdown(), the Java process could keep
             * running after the tests are finished.
             */
            executorService.shutdown();
        }
    }

    /*
     * Returns the first visited location close enough
     * to the given attraction.
     *
     * Once a match is found, the method stops searching.
     */
    private VisitedLocation findMatchingLocation(
            List<VisitedLocation> userLocations,
            Attraction attraction) {

        for (VisitedLocation visitedLocation : userLocations) {

            if (nearAttraction(
                    visitedLocation,
                    attraction
            )) {
                return visitedLocation;
            }
        }

        return null;
    }

    public boolean isWithinAttractionProximity(
            Attraction attraction,
            Location location) {

        return getDistance(attraction, location)
                <= ATTRACTION_PROXIMITY_RANGE;
    }

    private boolean nearAttraction(
            VisitedLocation visitedLocation,
            Attraction attraction) {

        return getDistance(
                attraction,
                visitedLocation.location
        ) <= proximityBuffer;
    }

    /*
     * This method is public because the nearby-attractions
     * response also needs the possible reward points.
     */
    public int getAttractionRewardPoints(
            Attraction attraction,
            UUID userId) {

        return rewardsCentral.getAttractionRewardPoints(
                attraction.attractionId,
                userId
        );
    }

    public double getDistance(
            Location location1,
            Location location2) {

        double latitude1 =
                Math.toRadians(location1.latitude);

        double longitude1 =
                Math.toRadians(location1.longitude);

        double latitude2 =
                Math.toRadians(location2.latitude);

        double longitude2 =
                Math.toRadians(location2.longitude);

        double angle = Math.acos(
                Math.sin(latitude1) * Math.sin(latitude2)
                        + Math.cos(latitude1)
                        * Math.cos(latitude2)
                        * Math.cos(longitude1 - longitude2)
        );

        double nauticalMiles =
                60 * Math.toDegrees(angle);

        return STATUTE_MILES_PER_NAUTICAL_MILE
                * nauticalMiles;
    }
}