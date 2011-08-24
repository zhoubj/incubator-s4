/*
 * Copyright (c) 2011 Yahoo! Inc. All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *          http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License. See accompanying LICENSE file. 
 */
package io.s4.example.counter;

import io.s4.core.App;
import io.s4.core.Event;
import io.s4.core.ProcessingElement;
import io.s4.core.Stream;

public class CounterPE extends ProcessingElement {

    final private Stream<CountEvent> countStream;
    private int interval;

    public CounterPE(App app, int interval, Stream<CountEvent> countStream) {
        super(app);
        this.countStream = countStream;
        this.interval = interval;
    }

    private long counter = 0;

    /*
     * (non-Javadoc)
     * 
     * @see io.s4.ProcessingElement#processInputEvent(io.s4.Event)
     */
    @Override
    protected void processInputEvent(Event event) {

        counter += 1;

        if (counter % interval == 0) {
            CountEvent countEvent = new CountEvent(countStream.getName() + ": "
                    + getId(), counter);
            countStream.put(countEvent);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see io.s4.ProcessingElement#sendOutputEvent()
     */
    @Override
    public void processOutputEvent(Event event) {

    }

    /*
     * (non-Javadoc)
     * 
     * @see io.s4.ProcessingElement#init()
     */
    @Override
    protected void onCreate() {
        // TODO Auto-generated method stub

    }

    @Override
    protected void onRemove() {

    }
}
