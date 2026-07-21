package com.openclassrooms.tourguide.user;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import gpsUtil.location.VisitedLocation;
import tripPricer.Provider;

public class User {

    private final UUID userId;
    private final String userName;
    private final String phoneNumber;
    private final String emailAddress;

    /*
     * CopyOnWriteArrayList is thread-safe.
     *
     * It allows one thread to read the list while another thread
     * adds a new visited location.
     *
     * This prevents ConcurrentModificationException.
     */
    private final List<VisitedLocation> visitedLocations =
            new CopyOnWriteArrayList<>();

    /*
     * Rewards can also be read and modified by different threads.
     *
     * CopyOnWriteArrayList makes these operations safer.
     */
    private final List<UserReward> userRewards =
            new CopyOnWriteArrayList<>();

    private UserPreferences userPreferences = new UserPreferences();

    /*
     * Trip deals are not part of the current concurrency problem,
     * so a normal ArrayList is enough for now.
     */
    private List<Provider> tripDeals = new ArrayList<>();

    public User(UUID userId, String userName, String phoneNumber, String emailAddress) {
        this.userId = userId;
        this.userName = userName;
        this.phoneNumber = phoneNumber;
        this.emailAddress = emailAddress;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public List<VisitedLocation> getVisitedLocations() {
        return visitedLocations;
    }

    public void addToVisitedLocations(VisitedLocation visitedLocation) {
        visitedLocations.add(visitedLocation);
    }

    public VisitedLocation getLastVisitedLocation() {
        int lastIndex = visitedLocations.size() - 1;
        return visitedLocations.get(lastIndex);
    }

    public List<UserReward> getUserRewards() {
        return userRewards;
    }

    public void addUserReward(UserReward userReward) {

        /*
         * Check whether this attraction already has a reward.
         *
         * We compare the attraction names because they are Strings
         * and represent the same attraction in this application.
         */
        boolean rewardAlreadyExists = userRewards.stream()
                .anyMatch(existingReward ->
                        existingReward.attraction.attractionName.equals(
                                userReward.attraction.attractionName
                        )
                );

        /*
         * Add the reward only when it does not already exist.
         */
        if (!rewardAlreadyExists) {
            userRewards.add(userReward);
        }
    }

    public UserPreferences getUserPreferences() {
        return userPreferences;
    }

    public void setUserPreferences(UserPreferences userPreferences) {
        this.userPreferences = userPreferences;
    }

    public List<Provider> getTripDeals() {
        return tripDeals;
    }

    public void setTripDeals(List<Provider> tripDeals) {
        this.tripDeals = tripDeals;
    }
}