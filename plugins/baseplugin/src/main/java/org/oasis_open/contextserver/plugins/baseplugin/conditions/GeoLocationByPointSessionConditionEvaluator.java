package org.oasis_open.contextserver.plugins.baseplugin.conditions;

/*
 * #%L
 * context-server-persistence-elasticsearch-core
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.apache.commons.beanutils.BeanUtils;
import org.elasticsearch.common.geo.GeoDistance;
import org.elasticsearch.common.unit.DistanceUnit;
import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionEvaluator;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionEvaluatorDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class GeoLocationByPointSessionConditionEvaluator implements ConditionEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(GeoLocationByPointSessionConditionEvaluator.class.getName());

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {
        try {
            String type = (String) condition.getParameter("type");
            Double latitudeProperty = Double.parseDouble(BeanUtils.getProperty(item, "properties.location.lat"));
            Double longitudeProperty = Double.parseDouble(BeanUtils.getProperty(item, "properties.location.lon"));

            if("circle".equals(type)) {
                Double circleLatitude = (Double) condition.getParameter("circleLatitude");
                Double circleLongitude = (Double) condition.getParameter("circleLongitude");
                DistanceUnit.Distance distance = DistanceUnit.Distance.parseDistance(condition.getParameter("distance").toString());

                double d = GeoDistance.DEFAULT.calculate(circleLatitude, circleLongitude, latitudeProperty, longitudeProperty, distance.unit);
                return d < distance.value;
            } else if("rectangle".equals(type)) {
                Double rectLatitudeNE = (Double) condition.getParameter("rectLatitudeNE");
                Double rectLongitudeNE = (Double) condition.getParameter("rectLongitudeNE");
                Double rectLatitudeSW = (Double) condition.getParameter("rectLatitudeSW");
                Double rectLongitudeSW = (Double) condition.getParameter("rectLongitudeSW");

                if(rectLatitudeNE != null && rectLongitudeNE != null && rectLatitudeSW != null && rectLongitudeSW != null) {
                    return latitudeProperty < Math.max(rectLatitudeNE, rectLatitudeSW)  &&
                            latitudeProperty > Math.min(rectLatitudeNE, rectLatitudeSW) &&
                            longitudeProperty < Math.max(rectLongitudeNE, rectLongitudeSW) &&
                            longitudeProperty > Math.min(rectLongitudeNE, rectLongitudeSW);
                }
            }
        } catch (Exception e) {
            logger.debug("Cannot get properties", e);
        }
        return false;
    }


}
