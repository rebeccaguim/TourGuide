package com.openclassrooms.tourguide;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.StopWatch;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
@Disabled("Disabled while validating GitHub Actions pipeline")
public class TestPerformance {

    private static final int PERFORMANCE_USER_NUMBER = 100_000;

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

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        TourGuideService tourGuideService =
                new TourGuideService(
                        gpsUtil,
                        rewardsService
                );

        /*
         * The performance test tracks users manually.
         *
         * The background tracker is stopped to avoid
         * processing the same users at the same time.
         */
        tourGuideService.tracker.stopTracking();

        List<User> allUsers =
                new ArrayList<>(
                        tourGuideService.getAllUsers()
                );

        for (User user : allUsers) {
            tourGuideService.trackUserLocation(user);
        }

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

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        TourGuideService tourGuideService =
                new TourGuideService(
                        gpsUtil,
                        rewardsService
                );

        /*
         * Rewards are calculated directly by this test.
         *
         * The background tracker is stopped to avoid
         * additional reward calculations.
         */
        tourGuideService.tracker.stopTracking();

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
         * Calculate rewards with concurrent batches.
         */
        rewardsService.calculateRewards(allUsers);

        for (User user : allUsers) {
            assertTrue(
                    user.getUserRewards().size() > 0
            );
        }

        stopWatch.stop();

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