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


package dk.dma.ais.abnormal.event.db.h2;

import dk.dma.ais.abnormal.event.db.EventRepository;
import dk.dma.ais.abnormal.event.db.domain.AbnormalShipSizeOrTypeEvent;
import dk.dma.ais.abnormal.event.db.domain.Behaviour;
import dk.dma.ais.abnormal.event.db.domain.Event;
import dk.dma.ais.abnormal.event.db.domain.Position;
import dk.dma.ais.abnormal.event.db.domain.Vessel;
import dk.dma.ais.abnormal.event.db.domain.VesselId;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.ServiceRegistryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class H2EventRepository implements EventRepository {

    private static final Logger LOG = LoggerFactory.getLogger(H2EventRepository.class);
    {
        LOG.info(this.getClass().getSimpleName() + " created (" + this + ").");
    }

    private final SessionFactory sessionFactory;

    public H2EventRepository() {
        try{
            LOG.debug("Loading Hibernate configuration.");
            Configuration configuration = new Configuration()
                    .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                    .setProperty("hibernate.connection.url", "jdbc:h2:./db/repository")
                    .setProperty("hibernate.connection.username", "sa")
                    .setProperty("hibernate.connection.password", "")
                    .setProperty("hibernate.default_schema", "PUBLIC")
                    .setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
                    .setProperty("hibernate.show_sql", "true")
                    .setProperty("hibernate.hbm2ddl.auto", "update")
                    .setProperty("hibernate.order_updates", "true")
                    .addAnnotatedClass(AbnormalShipSizeOrTypeEvent.class)
                    .addAnnotatedClass(Vessel.class)
                    .addAnnotatedClass(VesselId.class)
                    .addAnnotatedClass(Behaviour.class)
                    .addAnnotatedClass(Position.class);
            ServiceRegistryBuilder serviceRegistryBuilder = new ServiceRegistryBuilder();
            serviceRegistryBuilder.applySettings(configuration.getProperties());
            ServiceRegistry serviceRegistry = serviceRegistryBuilder.buildServiceRegistry();
            LOG.info("Starting Hibernate.");
            sessionFactory = configuration.buildSessionFactory(serviceRegistry);
            LOG.info("Hibernate started.");

        }catch (Throwable ex) {
            System.err.println("Failed to create sessionFactory object." + ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Override
    protected void finalize() {
        LOG.info("Closing database session factory.");
        sessionFactory.close();
    }

    @Override
    public void save(Event event) {
        Session session = sessionFactory.openSession();
        session.beginTransaction();
        session.save(event);
        session.getTransaction().commit();
    }

    @Override
    public Set<Event> findActiveEventsByVessel(Vessel vessel) {
        return null;
    }

    //@Override
    public Set<Event> findOngoingEventByVessel(Vessel vessel, Class<? extends Event> eventClass) {
        // TODO
        return null;
    }

    @Override
    public boolean hasActiveEvent(Vessel vessel, Class<? extends Event> eventClass) {
        // TODO
        return false;
    }
}
