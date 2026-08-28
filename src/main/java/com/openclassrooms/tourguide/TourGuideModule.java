package com.openclassrooms.tourguide;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.openclassrooms.tourguide.service.RewardsService;

import gpsUtil.GpsUtil;
import rewardCentral.RewardCentral;

/**
 * Configures the external services used by the TourGuide application.
 * These beans can be injected into the application by Spring.
 */
@Configuration
public class TourGuideModule {

	/**
	 * Provides the GPS utility used to retrieve locations and attractions.
	 */
	@Bean
	public GpsUtil getGpsUtil() {
		return new GpsUtil();
	}

	/**
	 * Provides the service responsible for calculating user rewards.
	 */
	@Bean
	public RewardsService getRewardsService() {
		return new RewardsService(getGpsUtil(), getRewardCentral());
	}

	/**
	 * Provides the external service used to calculate reward points.
	 */
	@Bean
	public RewardCentral getRewardCentral() {
		return new RewardCentral();
	}

}