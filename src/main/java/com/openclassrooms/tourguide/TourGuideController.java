package com.openclassrooms.tourguide;

import java.util.List;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.NearbyAttraction;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import gpsUtil.location.VisitedLocation;
import tripPricer.Provider;

@RestController
public class TourGuideController {

    private final TourGuideService tourGuideService;

    /*
     * Spring provides TourGuideService
     * through constructor injection.
     */
    public TourGuideController(
            TourGuideService tourGuideService) {

        this.tourGuideService = tourGuideService;
    }

    @RequestMapping("/")
    public String index() {
        return "Greetings from TourGuide!";
    }

    @RequestMapping("/getLocation")
    public VisitedLocation getLocation(
            @RequestParam String userName) {

        return tourGuideService.getUserLocation(
                getUser(userName)
        );
    }

    /*
     * Return the five nearest attractions
     * with all information required by the project.
     */
    @RequestMapping("/getNearbyAttractions")
    public List<NearbyAttraction> getNearbyAttractions(
            @RequestParam String userName) {

        User user = getUser(userName);

        VisitedLocation visitedLocation =
                tourGuideService.getUserLocation(user);

        return tourGuideService
                .getNearbyAttractionsDetails(
                        visitedLocation
                );
    }

    @RequestMapping("/getRewards")
    public List<UserReward> getRewards(
            @RequestParam String userName) {

        return tourGuideService.getUserRewards(
                getUser(userName)
        );
    }

    @RequestMapping("/getTripDeals")
    public List<Provider> getTripDeals(
            @RequestParam String userName) {

        return tourGuideService.getTripDeals(
                getUser(userName)
        );
    }

    private User getUser(String userName) {
        return tourGuideService.getUser(userName);
    }
}