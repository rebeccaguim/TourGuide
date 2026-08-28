package com.openclassrooms.tourguide.user;

/**
 * Contains all information required for one nearby tourist attraction,
 * including its location, the user's location, the distance and reward points.
 */
public class NearbyAttraction {

    private final String attractionName;

    private final double attractionLatitude;
    private final double attractionLongitude;

    private final double userLatitude;
    private final double userLongitude;

    private final double distanceInKilometers;
    private final int rewardPoints;

    public NearbyAttraction(
            String attractionName,
            double attractionLatitude,
            double attractionLongitude,
            double userLatitude,
            double userLongitude,
            double distanceInKilometers,
            int rewardPoints) {

        this.attractionName = attractionName;
        this.attractionLatitude = attractionLatitude;
        this.attractionLongitude = attractionLongitude;
        this.userLatitude = userLatitude;
        this.userLongitude = userLongitude;
        this.distanceInKilometers = distanceInKilometers;
        this.rewardPoints = rewardPoints;
    }

    public String getAttractionName() {
        return attractionName;
    }

    public double getAttractionLatitude() {
        return attractionLatitude;
    }

    public double getAttractionLongitude() {
        return attractionLongitude;
    }

    public double getUserLatitude() {
        return userLatitude;
    }

    public double getUserLongitude() {
        return userLongitude;
    }

    public double getDistanceInKilometers() {
        return distanceInKilometers;
    }

    public int getRewardPoints() {
        return rewardPoints;
    }
}