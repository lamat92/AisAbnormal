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

package dk.dma.ais.abnormal.tracker;

import dk.dma.enav.model.geometry.Position;
import net.jcip.annotations.NotThreadSafe;

import java.util.HashMap;
import java.util.Map;

@NotThreadSafe
public abstract class TrackingReport implements Cloneable {

    private final Map<String, Object> properties = new HashMap<>(2);

    public Object getProperty(String propertyName) {
        return properties.get(propertyName);
    }

    public void setProperty(String propertyName, Object propertyValue) {
        properties.put(propertyName, propertyValue);
    }

    public void removeProperty(String propertyName) {
        properties.remove(propertyName);
    }

    // ---

    public abstract long getTimestamp();
    public abstract Position getPosition();
    public abstract float getCourseOverGround();
    public abstract float getSpeedOverGround();
}
