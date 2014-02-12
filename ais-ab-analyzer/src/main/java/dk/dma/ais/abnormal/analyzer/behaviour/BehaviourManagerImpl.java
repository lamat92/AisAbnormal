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
package dk.dma.ais.abnormal.analyzer.behaviour;

import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import dk.dma.ais.abnormal.analyzer.behaviour.events.AbnormalEventLower;
import dk.dma.ais.abnormal.analyzer.behaviour.events.AbnormalEventMaintain;
import dk.dma.ais.abnormal.analyzer.behaviour.events.AbnormalEventRaise;
import dk.dma.ais.abnormal.event.db.domain.Event;
import dk.dma.ais.abnormal.tracker.PositionReport;
import dk.dma.ais.abnormal.tracker.Track;
import dk.dma.ais.abnormal.tracker.TrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The behaviour manager keeps on an eye on the number of consecutive normal and abnormal behaviours of a track.
 * If more than the threshold of abnormal behaviours are observed, then a request to raise an event is posted on
 * the event bus (if no such event was already raised). If more than the the threshold of normal behaviours are
 * observed, then a request to lower an event is posted on the event bus (if an event was requested raised earlier).
 */
public class BehaviourManagerImpl implements BehaviourManager {

    static final Logger LOG = LoggerFactory.getLogger(BehaviourManagerImpl.class);
    {
        LOG.info(this.getClass().getSimpleName() + " created (" + this + ").");
    }

    TrackingService trackingService;
    EventBus eventBus = new EventBus();

    int line = 1;

    /**
     * Number of consecutive abnormal behaviours of track before an event is raised.
     */
    static final int RAISE_EVENT_SCORE_THRESHOLD = 3;

    /**
     * Number of consecutive normal behaviours of track before an event is lowered.
     */
    static final int LOWER_EVENT_SCORE_THRESHOLD = 5;

    private static final String SCORE_KEY_PREFIX = "event-score-";
    private static final String EVENT_STATE_KEY_PREFIX = "event-raised-";
    private static final String CERTAINTY_KEY_PREFIX = "event-certainty-";

    @Inject
    public BehaviourManagerImpl(TrackingService trackingService) {
        this.trackingService = trackingService;
        eventBus.register(this);
    }

    @Override
    public void abnormalBehaviourDetected(Class<? extends Event> eventClass, Track track) {
        if (!getEventRaised(eventClass, track)) {
            int score = getEventScore(eventClass, track);

            if (score < RAISE_EVENT_SCORE_THRESHOLD) {
                score++;
                if (score == RAISE_EVENT_SCORE_THRESHOLD) {
                    setEventRaised(eventClass, track, true);
                    setEventScore(eventClass, track, 0);
                    setEventCertaintyOnCurrentPositionReport(eventClass, track);
                    fireRaiseEvent(eventClass, track);
                } else {
                    setEventScore(eventClass, track, score);
                    setEventCertaintyOnCurrentPositionReport(eventClass, track);
                }
            }
        } else {
            setEventScore(eventClass, track, 0);
            setEventCertaintyOnCurrentPositionReport(eventClass, track);
            fireMaintainEvent(eventClass, track);
        }
    }

    @Override
    public void normalBehaviourDetected(Class<? extends Event> eventClass, Track track) {
        if (getEventRaised(eventClass, track)) {
            int score = getEventScore(eventClass, track);

            if (score < LOWER_EVENT_SCORE_THRESHOLD) {
                score++;
                if (score == LOWER_EVENT_SCORE_THRESHOLD) {
                    fireLowerEvent(eventClass, track);
                    removeEventRaised(eventClass, track);
                    removeEventScore(eventClass, track);
                    setEventCertaintyOnCurrentPositionReport(eventClass, track);
                } else {
                    setEventScore(eventClass, track, score);
                    setEventRaised(eventClass, track, true);
                    setEventCertaintyOnCurrentPositionReport(eventClass, track);
                    LOG.debug("fireMaintainEvent (normal) - " + track.getMmsi() + " - " + eventClass);
                    fireMaintainEvent(eventClass, track);
                }
            }
        } else {
            setEventScore(eventClass, track, 0);
            setEventCertaintyOnCurrentPositionReport(eventClass, track);
        }
    }

    @Override
    public void trackStaleDetected(Class<? extends Event> eventClass, Track track) {
        removeEventRaised(eventClass, track);
        removeEventScore(eventClass, track);
    }

    @Override
    public void registerSubscriber(Object subscriber) {
        eventBus.register(subscriber);
    }

    @Override
    public EventCertainty getEventCertaintyAtCurrentPosition(Class<? extends Event> eventClass, Track track) {
        EventCertainty eventCertainty = null;

        PositionReport positionReport = track.getPositionReport();
        if (positionReport != null) {
            String eventCertaintyKey = getEventCertaintyKey(eventClass);
            eventCertainty = (EventCertainty) positionReport.getProperty(eventCertaintyKey);
        }

        return eventCertainty == null ? EventCertainty.UNDEFINED : eventCertainty;
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void listen(DeadEvent event) {
        LOG.trace("No subscribers were interested in this event: " + event.getEvent());
    }

    private static EventCertainty getEventCertainty(Class<? extends Event> eventClass, Track track) {
        boolean eventRaised = getEventRaised(eventClass, track);
        int eventScore = getEventScore(eventClass, track);
        EventCertainty eventCertainty;

        if (eventRaised == false && eventScore == 0) {
            eventCertainty = EventCertainty.LOWERED;
        } else if (eventRaised == true && eventScore == 0) {
            eventCertainty = EventCertainty.RAISED;
        } else {
            eventCertainty = EventCertainty.UNCERTAIN;
        }

        return eventCertainty;
    }

    private void fireMaintainEvent(Class<? extends Event> eventClass, Track track) {
        eventBus.post(new AbnormalEventMaintain(eventClass, track, getEventCertainty(eventClass, track)));
    }

    private void fireRaiseEvent(Class<? extends Event> eventClass, Track track) {
        eventBus.post(new AbnormalEventRaise(eventClass, track, getEventCertainty(eventClass, track)));
    }

    private void fireLowerEvent(Class<? extends Event> eventClass, Track track) {
        eventBus.post(new AbnormalEventLower(eventClass, track, getEventCertainty(eventClass, track)));
    }

    public static String getEventRaisedKey(Class<? extends Event> eventClass) {
        return EVENT_STATE_KEY_PREFIX + eventClass.getSimpleName();
    }

    public static String getScoreKey(Class<? extends Event> eventClass) {
        return SCORE_KEY_PREFIX + eventClass.getSimpleName();
    }

    public static String getEventCertaintyKey(Class<? extends Event> eventClass) {
        return CERTAINTY_KEY_PREFIX + eventClass.getSimpleName();
    }

    /**
     * Remove the event-raised flag from the track.
     * @param eventClass
     * @param track
     */
    private static void removeEventRaised(Class<? extends Event> eventClass, Track track) {
        String eventRaisedKey = getEventRaisedKey(eventClass);
        track.removeProperty(eventRaisedKey);
    }

    /**
     * Set the value of the event-raised flag on the track
     * @param eventClass
     * @param track
     * @param raised
     */
    private static void setEventRaised(Class<? extends Event> eventClass, Track track, boolean raised) {
        String eventRaisedKey = getEventRaisedKey(eventClass);
        track.setProperty(eventRaisedKey, Boolean.valueOf(raised));
    }

    /**
     * Get the value of the event-raised flag on the track
     * @param eventClass
     * @param track
     * @return
     */
    private static boolean getEventRaised(Class<? extends Event> eventClass, Track track) {
        boolean eventRaised;

        String eventRaisedKey = getEventRaisedKey(eventClass);
        Boolean eventRaisedTmp = (Boolean) track.getProperty(eventRaisedKey);
        if (eventRaisedTmp == null) {
            eventRaised = false;
        } else {
            eventRaised = eventRaisedTmp.booleanValue();
        }

        return eventRaised;
    }

    /**
     * Remove the event score from the track.
     * @param eventClass
     * @param track
     */
    private static void removeEventScore(Class<? extends Event> eventClass, Track track) {
        String scoreKey = getScoreKey(eventClass);
        track.removeProperty(scoreKey);
    }

    /**
     * Set the event score on the track.
     * @param eventClass
     * @param track
     * @param score
     */
    private static void setEventScore(Class<? extends Event> eventClass, Track track, int score) {
        String scoreKey = getScoreKey(eventClass);
        track.setProperty(scoreKey, Integer.valueOf(score));
    }

    /**
     * Get the event score from the track.
     * @param eventClass
     * @param track
     * @return
     */
    private static int getEventScore(Class<? extends Event> eventClass, Track track) {
        int score;

        String scoreKey = getScoreKey(eventClass);

        Integer scoreTmp = (Integer) track.getProperty(scoreKey);
        if (scoreTmp == null) {
            score = 0;
        } else {
            score = scoreTmp.intValue();
        }

        return score;
    }

    /**
     * Set the event score on the most recent position report of the track.
     * @param eventClass
     * @param track
     */
    private static void setEventCertaintyOnCurrentPositionReport(Class<? extends Event> eventClass, Track track) {
        PositionReport positionReport = track.getPositionReport();
        if (positionReport != null) {
            positionReport.setProperty(getEventCertaintyKey(eventClass), getEventCertainty(eventClass, track));
        }
    }
}
