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
     */
    private static final int REWARD_THREAD_POOL_SIZE = 100;

    /*
     * Maximum number of asynchronous tasks created
     * at the same time.
     *
     * Users are processed in batches to avoid creating
     * too many CompletableFuture objects in memory.
     */
    private static final int USER_BATCH_SIZE = 1000;

    private int proximityBuffer = DEFAULT_PROXIMITY_BUFFER;

    private final RewardCentral rewardsCentral;

    /*
     * Attractions are loaded only once.
     *
     * The attraction list does not change during execution,
     * so it can be reused for every user.
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
     * Calculates rewards for one user.
     */
    public void calculateRewards(User user) {

        List<VisitedLocation> userLocations =
                user.getVisitedLocations();

        /*
         * Store attraction IDs that already have a reward.
         *
         * A HashSet avoids repeatedly searching
         * the complete reward list.
         */
        Set<UUID> rewardedAttractionIds = new HashSet<>();

        for (UserReward userReward : user.getUserRewards()) {
            rewardedAttractionIds.add(
                    userReward.attraction.attractionId
            );
        }

        /*
         * Check every attraction until a matching
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
                 * Add the attraction ID immediately
                 * to prevent duplicate rewards.
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
     * Users are divided into smaller batches.
     * Each batch is completed before the next one starts.
     *
     * This avoids creating one hundred thousand
     * CompletableFuture objects at the same time.
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

            /*
             * Move through the user list one batch at a time.
             */
            for (int startIndex = 0;
                    startIndex < users.size();
                    startIndex += USER_BATCH_SIZE) {

                /*
                 * The last batch can contain fewer
                 * than one thousand users.
                 */
                int endIndex = Math.min(
                        startIndex + USER_BATCH_SIZE,
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
                                                () -> calculateRewards(user),
                                                executorService
                                        )
                                )
                                .toArray(
                                        CompletableFuture[]::new
                                );

                /*
                 * Wait for the current batch
                 * before starting the next batch.
                 */
                CompletableFuture.allOf(futures).join();
            }

        } finally {

            /*
             * Stop the thread pool after all batches
             * have been processed.
             */
            executorService.shutdown();
        }
    }

    /*
     * Returns the first visited location close enough
     * to the given attraction.
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