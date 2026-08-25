package com.openclassrooms.tourguide;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.StopWatch;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;

public class TestPerformance {

    private static final int PERFORMANCE_USER_NUMBER = 100;

    /*
     * Performance objectives:
     *
     * - Track 100,000 users in less than 15 minutes.
     * - Calculate rewards for 100,000 users
     *   in less than 20 minutes.
     */

 @Test
public void highVolumeTrackLocation() {

    GpsUtil gpsUtil = new GpsUtil();

    RewardsService rewardsService =
            new RewardsService(
                    gpsUtil,
                    new RewardCentral()
            );

    InternalTestHelper.setInternalUserNumber(
            PERFORMANCE_USER_NUMBER
    );

    /*
     * Disable the automatic background tracker.
     *
     * The performance test controls the tracking process
     * and must not process the same users twice.
     */
    TourGuideService tourGuideService =
            new TourGuideService(
                    gpsUtil,
                    rewardsService,
                    false
            );

    List<User> allUsers =
            new ArrayList<>(
                    tourGuideService.getAllUsers()
            );

    StopWatch stopWatch = new StopWatch();
    stopWatch.start();

    /*
     * The test only calls the application service.
     *
     * The concurrent implementation is located
     * inside TourGuideService.
     */
    tourGuideService.trackAllUsersLocations(
            allUsers
    );

    stopWatch.stop();

    long elapsedSeconds =
            TimeUnit.MILLISECONDS.toSeconds(
                    stopWatch.getTime()
            );

    System.out.println(
            "highVolumeTrackLocation: Time Elapsed: "
                    + elapsedSeconds
                    + " seconds."
    );

    assertTrue(
            TimeUnit.MINUTES.toSeconds(15)
                    >= elapsedSeconds
    );
}
@Test
public void highVolumeGetRewards() {

    GpsUtil gpsUtil = new GpsUtil();

    RewardsService rewardsService =
            new RewardsService(
                    gpsUtil,
                    new RewardCentral()
            );

    InternalTestHelper.setInternalUserNumber(
            PERFORMANCE_USER_NUMBER
    );

    /*
     * Disable the automatic background tracker.
     *
     * The reward performance test must not run
     * additional tracking tasks in background.
     */
    TourGuideService tourGuideService =
            new TourGuideService(
                    gpsUtil,
                    rewardsService,
                    false
            );

    Attraction attraction =
            gpsUtil.getAttractions().get(0);

    List<User> allUsers =
            new ArrayList<>(
                    tourGuideService.getAllUsers()
            );

    /*
     * Place every user directly on an attraction.
     *
     * This guarantees that every user should
     * receive at least one reward.
     */
    for (User user : allUsers) {

        VisitedLocation visitedLocation =
                new VisitedLocation(
                        user.getUserId(),
                        attraction,
                        new Date()
                );

        user.addToVisitedLocations(
                visitedLocation
        );
    }

    /*
     * Start the timer just before
     * the reward calculation.
     */
    StopWatch stopWatch = new StopWatch();
    stopWatch.start();

    /*
     * The concurrent implementation is located
     * inside RewardsService.
     */
    rewardsService.calculateRewards(
            allUsers
    );

    stopWatch.stop();

    /*
     * Verify that every user received
     * at least one reward.
     */
    for (User user : allUsers) {

        assertTrue(
                user.getUserRewards().size() > 0
        );
    }

    long elapsedSeconds =
            TimeUnit.MILLISECONDS.toSeconds(
                    stopWatch.getTime()
            );

    System.out.println(
            "highVolumeGetRewards: Time Elapsed: "
                    + elapsedSeconds
                    + " seconds."
    );

    /*
     * Performance objective:
     * 100,000 users in less than 20 minutes.
     */
    assertTrue(
            TimeUnit.MINUTES.toSeconds(20)
                    >= elapsedSeconds
    );
}
}