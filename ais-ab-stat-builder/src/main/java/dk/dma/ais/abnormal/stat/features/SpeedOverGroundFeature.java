/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */

package dk.dma.ais.abnormal.stat.features;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import dk.dma.ais.abnormal.stat.AppStatisticsService;
import dk.dma.ais.abnormal.stat.db.FeatureDataRepository;
import dk.dma.ais.abnormal.stat.db.data.FeatureData;
import dk.dma.ais.abnormal.stat.db.data.ShipTypeAndSizeFeatureData;
import dk.dma.ais.abnormal.stat.db.data.SpeedOverGroundFeatureData;
import dk.dma.ais.abnormal.tracker.Track;
import dk.dma.ais.abnormal.tracker.TrackingService;
import dk.dma.ais.abnormal.tracker.events.CellIdChangedEvent;
import dk.dma.ais.abnormal.util.Categorizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class SpeedOverGroundFeature implements Feature {

    /**
     * The logger
     */
    private static final transient Logger LOG = LoggerFactory.getLogger(SpeedOverGroundFeature.class);

    private final transient AppStatisticsService appStatisticsService;
    private final transient FeatureDataRepository featureDataRepository;
    private final transient TrackingService trackingService;

    private final transient AtomicBoolean started = new AtomicBoolean(false);

    static final String FEATURE_NAME = SpeedOverGroundFeature.class.getSimpleName();

    @Inject
    public SpeedOverGroundFeature(AppStatisticsService appStatisticsService, TrackingService trackingService, FeatureDataRepository featureDataRepository) {
        this.appStatisticsService = appStatisticsService;
        this.trackingService = trackingService;
        this.featureDataRepository = featureDataRepository;
    }

    /**
     * Start listening to tracking events.
     */
    public void start() {
        if (!started.get()) {
            trackingService.registerSubscriber(this);
            started.getAndSet(true);
        }
    }

    /*
     * This feature is only updated once per cell; i.e. when a cell is entered.
     */
    @AllowConcurrentEvents
    @Subscribe
    public void onCellIdChanged(CellIdChangedEvent event) {
        appStatisticsService.incFeatureStatistics(FEATURE_NAME, "Events processed");

        Track track = event.getTrack();
        Float sog = (Float) track.getProperty(Track.SPEED_OVER_GROUND);

        if (sog != null) {
            Long cellId = (Long) track.getProperty(Track.CELL_ID);
            Integer shipType = (Integer) track.getProperty(Track.SHIP_TYPE);
            Integer shipLength = (Integer) track.getProperty(Track.VESSEL_LENGTH);

            if (isInputValid(cellId, shipType, shipLength, sog)) {
                short shipTypeBucket = Categorizer.mapShipTypeToCategory(shipType);
                short shipSizeBucket = Categorizer.mapShipLengthToCategory(shipLength);
                short sogBucket = Categorizer.mapSpeedOverGroundToCategory(sog);

                incrementFeatureStatistics(cellId, shipTypeBucket, shipSizeBucket, sogBucket);

                appStatisticsService.incFeatureStatistics(this.getClass().getSimpleName(), "Events processed ok");
            }
        }
    }

    private boolean isInputValid(Long cellId, Integer shipType, Integer shipLength, Float sog) {
        boolean valid = true;

        if (cellId == null) {
            appStatisticsService.incFeatureStatistics(FEATURE_NAME, "Unknown mmsi");
            valid = false;
        }

        if (shipType == null) {
            appStatisticsService.incFeatureStatistics(FEATURE_NAME, "Unknown ship type");
            valid = false;
        }

        if (shipLength == null) {
            appStatisticsService.incFeatureStatistics(FEATURE_NAME, "Unknown ship length");
            valid = false;
        }

        if (sog == null) {
            appStatisticsService.incFeatureStatistics(FEATURE_NAME, "Unknown speed over ground");
            valid = false;
        }

        return valid;
    }

    private void incrementFeatureStatistics(long cellId, int shipTypeBucket, int shipSizeBucket, int sogBucket) {
        FeatureData featureDataTmp = featureDataRepository.getFeatureData(FEATURE_NAME, cellId);
        if (!(featureDataTmp instanceof SpeedOverGroundFeatureData)) {
            LOG.debug("No suitable feature data for cell id " + cellId + " found in repo. Creating new.");
            featureDataTmp = SpeedOverGroundFeatureData.create();
        }
        SpeedOverGroundFeatureData featureData = (SpeedOverGroundFeatureData) featureDataTmp;

        // Increment ship count
        featureData.incrementValue(shipTypeBucket - 1, shipSizeBucket - 1, sogBucket - 1, ShipTypeAndSizeFeatureData.STAT_SHIP_COUNT);

        LOG.debug("Storing feature data for cellId " + cellId + ", featureName " + FEATURE_NAME);
        featureDataRepository.putFeatureData(FEATURE_NAME, cellId, featureData);
        LOG.debug("Feature data for cellId " + cellId + ", featureName " + FEATURE_NAME + " stored.");
    }

}
