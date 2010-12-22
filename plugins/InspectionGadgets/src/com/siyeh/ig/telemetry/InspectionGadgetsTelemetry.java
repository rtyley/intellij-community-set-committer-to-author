/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.telemetry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class InspectionGadgetsTelemetry {

    private final ConcurrentHashMap<String, InspectionRunTime> inspectionRunTimes =
            new ConcurrentHashMap();

    public List<InspectionRunTime> buildList() {
        if (inspectionRunTimes.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        return new ArrayList(inspectionRunTimes.values());
    }

    public void reportRun(String inspectionID, long runTime) {
        InspectionRunTime inspectionRunTime =
                inspectionRunTimes.get(inspectionID);
        if (inspectionRunTime == null) {
            inspectionRunTime = new InspectionRunTime(inspectionID);
            final InspectionRunTime oldValue =
                    inspectionRunTimes.putIfAbsent(inspectionID,
                            inspectionRunTime);
            if (oldValue != null) {
                inspectionRunTime = oldValue;
            }
        }
        inspectionRunTime.addRunTime(runTime);
    }

    public void reset() {
        inspectionRunTimes.clear();
    }
}