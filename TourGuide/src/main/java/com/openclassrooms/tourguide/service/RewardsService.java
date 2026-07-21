package com.openclassrooms.tourguide.service;

import java.util.List;
import java.util.UUID;

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

    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

    /*
     * Maximum distance used to decide whether a user
     * actually visited an attraction.
     */
    private static final int DEFAULT_PROXIMITY_BUFFER = 10;

    /*
     * Maximum distance used by the original application
     * to identify nearby attractions.
     */
    private static final int ATTRACTION_PROXIMITY_RANGE = 200;

    private int proximityBuffer = DEFAULT_PROXIMITY_BUFFER;

    private final GpsUtil gpsUtil;
    private final RewardCentral rewardsCentral;

    public RewardsService(
            GpsUtil gpsUtil,
            RewardCentral rewardsCentral) {

        this.gpsUtil = gpsUtil;
        this.rewardsCentral = rewardsCentral;
    }

    public void setProximityBuffer(int proximityBuffer) {
        this.proximityBuffer = proximityBuffer;
    }

    public void setDefaultProximityBuffer() {
        proximityBuffer = DEFAULT_PROXIMITY_BUFFER;
    }

    public void calculateRewards(User user) {

        List<VisitedLocation> userLocations =
                user.getVisitedLocations();

        List<Attraction> attractions =
                gpsUtil.getAttractions();

        /*
         * Check every visited location against every attraction.
         */
        for (VisitedLocation visitedLocation : userLocations) {

            for (Attraction attraction : attractions) {

                boolean rewardAlreadyExists =
                        user.getUserRewards()
                                .stream()
                                .anyMatch(reward ->
                                        reward.attraction.attractionName
                                                .equals(attraction.attractionName)
                                );

                /*
                 * Add a reward only when:
                 * - it does not already exist;
                 * - the user was close enough to the attraction.
                 */
                if (!rewardAlreadyExists
                        && nearAttraction(visitedLocation, attraction)) {

                    int rewardPoints =
                            getAttractionRewardPoints(
                                    attraction,
                                    user.getUserId()
                            );

                    UserReward userReward =
                            new UserReward(
                                    visitedLocation,
                                    attraction,
                                    rewardPoints
                            );

                    user.addUserReward(userReward);
                }
            }
        }
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
     * This method is public because the controller response
     * also needs the possible reward points for each attraction.
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