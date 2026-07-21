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

    /*
     * Performance objectives:
     *
     * - Track 100,000 users in less than 15 minutes.
     * - Calculate rewards for 100,000 users
     *   in less than 20 minutes.
     *
     * The number of users should be increased gradually
     * while developing the performance solution.
     */

    @Test
    public void highVolumeTrackLocation() {

        GpsUtil gpsUtil = new GpsUtil();

        RewardsService rewardsService =
                new RewardsService(
                        gpsUtil,
                        new RewardCentral()
                );

        /*
         * Start with a small number while validating
         * the implementation.
         */
        InternalTestHelper.setInternalUserNumber(100000);

        TourGuideService tourGuideService =
                new TourGuideService(
                        gpsUtil,
                        rewardsService
                );

        List<User> allUsers =
                new ArrayList<>(
                        tourGuideService.getAllUsers()
                );

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        for (User user : allUsers) {
            tourGuideService.trackUserLocation(user);
        }

        stopWatch.stop();

        tourGuideService.tracker.stopTracking();

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

        /*
         * Start with 100 users.
         *
         * We will increase this value gradually after
         * confirming that the concurrent solution works.
         */
        InternalTestHelper.setInternalUserNumber(1000000);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        TourGuideService tourGuideService =
                new TourGuideService(
                        gpsUtil,
                        rewardsService
                );

        Attraction attraction =
                gpsUtil.getAttractions().get(0);

        List<User> allUsers =
                new ArrayList<>(
                        tourGuideService.getAllUsers()
                );

        /*
         * Place every user directly on an attraction
         * so that every user must receive a reward.
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
         * Calculate all users concurrently.
         *
         * The method waits until every user has finished.
         */
        rewardsService.calculateRewards(allUsers);

        for (User user : allUsers) {
            assertTrue(
                    user.getUserRewards().size() > 0
            );
        }

        stopWatch.stop();

        tourGuideService.tracker.stopTracking();

        long elapsedSeconds =
                TimeUnit.MILLISECONDS.toSeconds(
                        stopWatch.getTime()
                );

        System.out.println(
                "highVolumeGetRewards: Time Elapsed: "
                        + elapsedSeconds
                        + " seconds."
        );

        assertTrue(
                TimeUnit.MINUTES.toSeconds(20)
                        >= elapsedSeconds
        );
    }
}